package com.georgia.jeogiyo.order.service;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.order.dto.request.OrderCreateRequest;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceConcurrencyTest {

    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("동시에 주문해도 재고보다 많은 주문은 생성되지 않는다")
    void createOrder_concurrentStockDecrease_successOnlyOne() throws InterruptedException {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 6);

        User owner = userRepository.save(createUser("own" + suffix, Role.OWNER));
        User customer = userRepository.save(createUser("cus" + suffix, Role.CUSTOMER));

        Category category = categoryRepository.save(new Category("동시성" + suffix));

        Store store = new Store(
                owner,
                category,
                "동시성 테스트 가게",
                "서울특별시 종로구 세종대로 172",
                "02-0000-0000"
        );
        store.changeStatus(StoreStatus.OPEN);
        Store savedStore = storeRepository.save(store);

        Address address = addressRepository.save(Address.create(
                customer,
                new AddressCreateRequest(
                        "서울특별시 종로구 세종대로 172",
                        "101호",
                        "03154",
                        true
                )
        ));

        Product product = productRepository.save(new Product(
                savedStore,
                category,
                "재고 1개 상품",
                "동시성 테스트 상품",
                12000,
                1,
                false
        ));

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    orderService.createOrder(
                            customer.getLoginId(),
                            orderRequest(savedStore.getStoreId(), address.getAddressId(), product.getProductId())
                    );

                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        Product savedProduct = productRepository.findByProductIdAndIsDeletedFalse(product.getProductId())
                .orElseThrow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(savedProduct.getStock()).isZero();
    }

    private User createUser(String loginId, Role role) {
        UserSignupRequest request = new UserSignupRequest(
                loginId,
                "Test1234!",
                loginId + "Nick",
                "010-0000-0000",
                loginId + "@test.com"
        );

        User user = User.customerCreate(request, passwordEncoder);
        user.changeRole(role);
        return user;
    }

    private OrderCreateRequest orderRequest(
            java.util.UUID storeId,
            java.util.UUID addressId,
            java.util.UUID productId
    ) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setStoreId(storeId);
        request.setAddressId(addressId);

        OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);

        request.setItems(List.of(item));
        return request;
    }
}