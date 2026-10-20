package com.georgia.jeogiyo.payment.service;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.order.dto.request.OrderCancelRequest;
import com.georgia.jeogiyo.order.dto.request.OrderCreateRequest;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.order.repository.OrderRepository;
import com.georgia.jeogiyo.order.service.OrderService;
import com.georgia.jeogiyo.payment.entity.PaymentMethod;
import com.georgia.jeogiyo.payment.entity.PaymentStatus;
import com.georgia.jeogiyo.payment.repository.PaymentRepository;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.support.DomainTestFixture;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:payment-order-race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "SECURITY_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gemini.api.key=test"
})
@Import(PaymentOrderConcurrencyTest.GateConfiguration.class)
class PaymentOrderConcurrencyTest {
    @Autowired private PaymentService paymentService;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ProductRepository productRepository;

    @Autowired private Gates gates;

    private String loginId;
    private UUID orderId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        gates.beforeSave = null;
        gates.beforeRestore = null;
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(user("o" + suffix, Role.OWNER));
        User customer = userRepository.save(user("c" + suffix, Role.CUSTOMER));
        loginId = customer.getLoginId();
        Category category = categoryRepository.save(new Category("race" + suffix));
        Store store = new Store(owner, category, "동시성 테스트", "서울특별시 종로구 세종대로 172", "02-0000-0000");
        store.changeStatus(StoreStatus.OPEN);
        store = storeRepository.save(store);
        Address address = addressRepository.save(Address.create(customer,
                new AddressCreateRequest("서울특별시 종로구 세종대로 172", "101호", "03154", true)));
        Product product = productRepository.save(new Product(store, category, "테스트 상품", "설명", 12000, 1, false));
        productId = product.getProductId();
        OrderCreateRequest request = new OrderCreateRequest();
        request.setStoreId(store.getStoreId());
        request.setAddressId(address.getAddressId());
        OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        request.setItems(List.of(item));
        orderId = orderService.createOrder(loginId, request).getOrderId();
    }

    @Test
    @DisplayName("결제 저장 중 주문 취소가 요청되면 결제 커밋 후 취소되어 성공 결제가 남지 않는다")
    void paymentFirst_cancelWaitsAndCancelsPayment() throws Exception {
        CountDownLatch beforeSave = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);
        gates.beforeSave = () -> {
            beforeSave.countDown();
            await(releasePayment);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> payment = executor.submit(this::pay);
            await(beforeSave);
            CountDownLatch cancelStarted = new CountDownLatch(1);
            Future<?> cancellation = executor.submit(() -> {
                cancelStarted.countDown();
                cancel();
            });
            await(cancelStarted);
            try {
                assertThatThrownBy(() -> cancellation.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                releasePayment.countDown();
            }
            payment.get(10, TimeUnit.SECONDS);
            cancellation.get(10, TimeUnit.SECONDS);
            assertCancelledAndStockRestored();
            assertThat(paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(orderId).orElseThrow().getPaymentStatus())
                    .isEqualTo(PaymentStatus.CANCEL);
        } finally {
            releasePayment.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("주문 취소 중 결제가 요청되면 취소 커밋 후 결제를 거절한다")
    void cancellationFirst_paymentWaitsAndFails() throws Exception {
        CountDownLatch beforeRestore = new CountDownLatch(1);
        CountDownLatch releaseCancellation = new CountDownLatch(1);
        gates.beforeRestore = () -> {
            beforeRestore.countDown();
            await(releaseCancellation);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancellation = executor.submit(this::cancel);
            await(beforeRestore);
            CountDownLatch paymentStarted = new CountDownLatch(1);
            Future<?> payment = executor.submit(() -> {
                paymentStarted.countDown();
                pay();
            });
            await(paymentStarted);
            try {
                assertThatThrownBy(() -> payment.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                releaseCancellation.countDown();
            }
            cancellation.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> payment.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .hasRootCauseMessage(GlobalErrorCode.INVALID_ORDER_STATUS_TRANSITION.getMessage());
            assertCancelledAndStockRestored();
            assertThat(paymentRepository.existsByOrder_OrderId(orderId)).isFalse();
        } finally {
            releaseCancellation.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void pay() {
        paymentService.createPayment(orderId, loginId, DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD));
    }

    private void cancel() {
        OrderCancelRequest request = new OrderCancelRequest();
        request.setCancelReason("동시 취소 테스트");
        orderService.cancelOrder(loginId, orderId, request);
    }

    private void assertCancelledAndStockRestored() {
        assertThat(orderRepository.findByOrderIdAndIsDeletedFalse(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(productRepository.findByProductIdAndIsDeletedFalse(productId).orElseThrow().getStock())
                .isEqualTo(1);
    }

    private User user(String id, Role role) {
        User user = User.customerCreate(new UserSignupRequest(id, "Test1234!", id + "Nick",
                "010-0000-0000", id + "@test.com"), passwordEncoder);
        user.changeRole(role);
        return user;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 테스트 동기화 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시성 테스트 중단", e);
        }
    }

    static class Gates implements BeanPostProcessor {
        volatile Runnable beforeSave;
        volatile Runnable beforeRestore;

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!beanName.equals("paymentRepository") && !beanName.equals("productRepository")) {
                return bean;
            }
            ProxyFactory proxy = new ProxyFactory(bean);
            proxy.addAdvice((MethodInterceptor) invocation -> {
                Runnable gate = null;
                if (beanName.equals("paymentRepository") && invocation.getMethod().getName().equals("save")) {
                    gate = beforeSave;
                } else if (beanName.equals("productRepository") && invocation.getMethod().getName().equals("increaseStock")) {
                    gate = beforeRestore;
                }
                if (gate != null) {
                    gate.run();
                }
                return invocation.proceed();
            });
            return proxy.getProxy();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GateConfiguration {
        // 실제 DB 호출을 유지하면서 트랜잭션의 경합 지점만 멈춘다.
        @Bean
        static Gates concurrencyGates() {
            return new Gates();
        }
    }
}
