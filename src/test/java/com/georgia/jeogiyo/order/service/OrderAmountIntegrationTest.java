package com.georgia.jeogiyo.order.service;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.category.repository.CategoryRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.order.dto.request.OrderCreateRequest;
import com.georgia.jeogiyo.order.repository.OrderRepository;
import com.georgia.jeogiyo.orderitem.repository.OrderItemRepository;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.user.dto.request.UserSignupRequest;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:order-amount;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "SECURITY_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gemini.api.key=test"
})
class OrderAmountIntegrationTest {
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User customer;
    private Store store;
    private Category category;
    private Address address;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(user("o" + suffix, Role.OWNER));
        customer = userRepository.save(user("c" + suffix, Role.CUSTOMER));
        category = categoryRepository.save(new Category("amount" + suffix));
        Store newStore = new Store(owner, category, "금액 테스트", "서울특별시 종로구 세종대로 172", "02-0000-0000");
        newStore.changeStatus(StoreStatus.OPEN);
        store = storeRepository.save(newStore);
        address = addressRepository.save(Address.create(customer,
                new AddressCreateRequest("서울특별시 종로구 세종대로 172", "101호", "03154", true)));
    }

    @ParameterizedTest
    @CsvSource({
            "1500000000,2,1,false",
            "1500000000,3,1,false",
            "1073741824,2,1,false",
            "1500000000,1,2,false",
            "1500000000,1,2,true",
            "1000000000,1,3,false"
    })
    @DisplayName("상품 금액 곱셈·주문 합산 범위 초과 시 주문을 거절하고 차감한 재고도 롤백한다")
    void overflowingAmount_rejectedAndRolledBack(int price, int quantity, int lineCount, boolean repeatProduct) {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < (repeatProduct ? 1 : lineCount); i++) {
            products.add(product(price, quantity * (repeatProduct ? lineCount : 1)));
        }
        List<OrderCreateRequest.OrderItemRequest> items = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            items.add(item(products.get(repeatProduct ? 0 : i), quantity));
        }
        long ordersBefore = orderRepository.count();
        long itemsBefore = orderItemRepository.count();

        assertThatThrownBy(() -> orderService.createOrder(customer.getLoginId(), request(items)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.ORDER_AMOUNT_EXCEEDED.getMessage());

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(orderItemRepository.count()).isEqualTo(itemsBefore);
        for (Product product : products) {
            assertThat(productRepository.findById(product.getProductId()).orElseThrow().getStock())
                    .isEqualTo(product.getStock());
        }
    }

    @ParameterizedTest
    @CsvSource({"2147483647,1", "1,2147483647", "12000,2"})
    @DisplayName("정상 금액과 Integer 최댓값은 주문·상품 스냅샷에 정확하게 저장한다")
    void amountWithinRange_savedExactly(int price, int quantity) {
        Product product = product(price, quantity);
        var response = orderService.createOrder(customer.getLoginId(), request(List.of(item(product, quantity))));
        int expected = Math.multiplyExact(price, quantity);
        assertThat(response.getTotalPrice()).isEqualTo(expected);
        assertThat(orderRepository.findById(response.getOrderId()).orElseThrow().getTotalPrice()).isEqualTo(expected);
        var items = orderItemRepository.findByOrderId(response.getOrderId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getUnitPrice()).isEqualTo(price);
        assertThat(items.get(0).getQuantity()).isEqualTo(quantity);
        assertThat(items.get(0).getItemTotalPrice()).isEqualTo(expected);
        assertThat(productRepository.findById(product.getProductId()).orElseThrow().getStock()).isZero();
    }

    @Test
    @DisplayName("여러 상품의 합산 결과가 Integer 최댓값과 같으면 허용한다")
    void sumAtUpperBound_savedExactly() {
        Product first = product(Integer.MAX_VALUE - 1, 1);
        Product second = product(1, 1);
        var response = orderService.createOrder(customer.getLoginId(), request(List.of(item(first, 1), item(second, 1))));
        assertThat(response.getTotalPrice()).isEqualTo(Integer.MAX_VALUE);
        assertThat(orderRepository.findById(response.getOrderId()).orElseThrow().getTotalPrice()).isEqualTo(Integer.MAX_VALUE);
        assertThat(orderItemRepository.findByOrderId(response.getOrderId()))
                .extracting(i -> i.getItemTotalPrice()).containsExactlyInAnyOrder(Integer.MAX_VALUE - 1, 1);
    }

    private Product product(int price, int stock) {
        return productRepository.save(new Product(store, category, "테스트 상품", "설명", price, stock, false));
    }

    private OrderCreateRequest.OrderItemRequest item(Product product, int quantity) {
        OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
        item.setProductId(product.getProductId());
        item.setQuantity(quantity);
        return item;
    }

    private OrderCreateRequest request(List<OrderCreateRequest.OrderItemRequest> items) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setStoreId(store.getStoreId());
        request.setAddressId(address.getAddressId());
        request.setItems(items);
        return request;
    }

    private User user(String id, Role role) {
        User user = User.customerCreate(new UserSignupRequest(id, "Test1234!", id + "Nick",
                "010-0000-0000", id + "@test.com"), passwordEncoder);
        user.changeRole(role);
        return user;
    }
}
