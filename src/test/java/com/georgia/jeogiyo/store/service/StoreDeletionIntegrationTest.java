package com.georgia.jeogiyo.store.service;

import com.georgia.jeogiyo.order.service.OrderService;

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

import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.order.dto.request.OrderStatusUpdateRequest;
import com.georgia.jeogiyo.payment.service.PaymentService;
import com.georgia.jeogiyo.payment.repository.PaymentRepository;
import com.georgia.jeogiyo.payment.entity.PaymentMethod;
import com.georgia.jeogiyo.payment.entity.PaymentStatus;
import com.georgia.jeogiyo.support.DomainTestFixture;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import java.util.concurrent.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:store-deletion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "SECURITY_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gemini.api.key=test"
})
@Import(StoreDeletionIntegrationTest.GateConfiguration.class)
class StoreDeletionIntegrationTest {
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private StoreService storeService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Gates gates;
    private User owner;
    private User customer;
    private Store store;
    private Category category;
    private Address address;

    @BeforeEach
    void setUp() {
        gates.beforeOrderSave = null;
        gates.afterActiveCheck = null;
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(user("o" + suffix, Role.OWNER));
        customer = userRepository.save(user("c" + suffix, Role.CUSTOMER));
        category = categoryRepository.save(new Category("amount" + suffix));
        Store newStore = new Store(owner, category, "금액 테스트", "서울특별시 종로구 세종대로 172", "02-0000-0000");
        newStore.changeStatus(StoreStatus.OPEN);
        store = storeRepository.save(newStore);
        address = addressRepository.save(Address.create(customer,
                new AddressCreateRequest("서울특별시 종로구 세종대로 172", "101호", "03154", true)));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("주문 완료·취소·거절만 삭제를 허용하고 나머지 진행 상태는 가게 삭제를 막는다")
    void deletionPolicy_allOrderStatuses(OrderStatus status) {
        Product product = product(12000, 1);
        UUID orderId = orderService.createOrder(customer.getLoginId(), request(List.of(item(product, 1)))).getOrderId();
        // 상태별 삭제 정책 검증용 데이터. 상태 전이 자체는 주문 서비스 테스트에서 검증한다.
        jdbcTemplate.update("update p_order set order_status = ? where order_id = ?", status.name(), orderId);
        boolean terminal = List.of(OrderStatus.ORDER_COMPLETED, OrderStatus.CANCELLED, OrderStatus.ORDER_REJECTED).contains(status);
        if (terminal) {
            storeService.deleteStore(store.getStoreId(), owner.getLoginId());
        } else {
            assertThatThrownBy(() -> storeService.deleteStore(store.getStoreId(), owner.getLoginId()))
                    .isInstanceOf(BusinessException.class).hasMessage(GlobalErrorCode.STORE_HAS_ACTIVE_ORDERS.getMessage());
        }
        assertThat(storeRepository.findById(store.getStoreId()).orElseThrow().isDeleted()).isEqualTo(terminal);
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MASTER"})
    @DisplayName("본인 가게 OWNER와 MASTER는 주문이 없는 가게를 삭제할 수 있다")
    void emptyStoreDeletion_allowed(Role role) {
        String actor = role == Role.OWNER ? owner.getLoginId()
                : userRepository.save(user("m" + UUID.randomUUID().toString().substring(0, 8), Role.MASTER)).getLoginId();
        storeService.deleteStore(store.getStoreId(), actor);
        assertThat(storeRepository.findById(store.getStoreId()).orElseThrow().isDeleted()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "OWNER"})
    @DisplayName("고객이나 다른 OWNER는 주문이 없어도 가게를 삭제할 수 없다")
    void unauthorizedDeletion_rejected(Role role) {
        User other = userRepository.save(user("u" + UUID.randomUUID().toString().substring(0, 8), role));
        assertThatThrownBy(() -> storeService.deleteStore(store.getStoreId(), other.getLoginId()))
                .isInstanceOf(BusinessException.class).hasMessage(GlobalErrorCode.FORBIDDEN_STORE.getMessage());
        assertThat(storeRepository.findById(store.getStoreId()).orElseThrow().isDeleted()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MASTER"})
    @DisplayName("결제된 주문이 있으면 관리자도 삭제할 수 없고 OWNER는 주문 처리를 계속할 수 있다")
    void paidOrderDeletion_rejectedAndOrderRemainsProcessable(Role role) {
        Product product = product(12000, 1);
        UUID orderId = orderService.createOrder(customer.getLoginId(), request(List.of(item(product, 1)))).getOrderId();
        paymentService.createPayment(orderId, customer.getLoginId(), DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD));
        String actor = role == Role.OWNER ? owner.getLoginId()
                : userRepository.save(user("m" + UUID.randomUUID().toString().substring(0, 8), Role.MASTER)).getLoginId();
        assertThatThrownBy(() -> storeService.deleteStore(store.getStoreId(), actor))
                .isInstanceOf(BusinessException.class).hasMessage(GlobalErrorCode.STORE_HAS_ACTIVE_ORDERS.getMessage());
        OrderStatusUpdateRequest update = new OrderStatusUpdateRequest();
        update.setOrderStatus(OrderStatus.ORDER_ACCEPTED);
        orderService.updateOrderStatus(owner.getLoginId(), orderId, update);
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ORDER_ACCEPTED);
        assertThat(paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(orderId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("주문 생성이 먼저 시작되면 가게 삭제는 주문 커밋을 기다린 뒤 거절된다")
    void orderFirst_deleteWaitsAndFails() throws Exception {
        Product product = product(12000, 1);
        CountDownLatch beforeSave = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        gates.beforeOrderSave = () -> { beforeSave.countDown(); await(release); };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> order = executor.submit(() -> orderService.createOrder(customer.getLoginId(), request(List.of(item(product, 1)))));
            await(beforeSave);
            CountDownLatch started = new CountDownLatch(1);
            Future<?> deletion = executor.submit(() -> {
                started.countDown();
                storeService.deleteStore(store.getStoreId(), owner.getLoginId());
            });
            await(started);
            try {
                assertThatThrownBy(() -> deletion.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            order.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> deletion.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(BusinessException.class)
                    .hasRootCauseMessage(GlobalErrorCode.STORE_HAS_ACTIVE_ORDERS.getMessage());
            assertThat(storeRepository.findById(store.getStoreId()).orElseThrow().isDeleted()).isFalse();
            assertThat(productRepository.findById(product.getProductId()).orElseThrow().getStock()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("가게 삭제가 먼저 시작되면 주문 생성은 삭제 커밋을 기다린 뒤 거절되고 재고를 차감하지 않는다")
    void deletionFirst_orderWaitsAndFails() throws Exception {
        Product product = product(12000, 1);
        long orderCount = orderRepository.count();
        CountDownLatch afterCheck = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        gates.afterActiveCheck = () -> { afterCheck.countDown(); await(release); };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = executor.submit(() -> storeService.deleteStore(store.getStoreId(), owner.getLoginId()));
            await(afterCheck);
            CountDownLatch started = new CountDownLatch(1);
            Future<?> order = executor.submit(() -> {
                started.countDown();
                orderService.createOrder(customer.getLoginId(), request(List.of(item(product, 1))));
            });
            await(started);
            try {
                assertThatThrownBy(() -> order.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            deletion.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> order.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(BusinessException.class)
                    .hasRootCauseMessage(GlobalErrorCode.NOT_FOUND_STORE.getMessage());
            assertThat(storeRepository.findById(store.getStoreId()).orElseThrow().isDeleted()).isTrue();
            assertThat(productRepository.findById(product.getProductId()).orElseThrow().getStock()).isEqualTo(1);
            assertThat(orderRepository.count()).isEqualTo(orderCount);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("동시성 테스트 동기화 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시성 테스트 중단", e);
        }
    }

    static class Gates implements BeanPostProcessor {
        volatile Runnable beforeOrderSave;
        volatile Runnable afterActiveCheck;

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            if (!name.equals("orderRepository")) return bean;
            ProxyFactory proxy = new ProxyFactory(bean);
            proxy.addAdvice((MethodInterceptor) invocation -> {
                if (invocation.getMethod().getName().equals("save") && beforeOrderSave != null) beforeOrderSave.run();
                Object result = invocation.proceed();
                if (invocation.getMethod().getName().equals("existsByStore_StoreIdAndOrderStatusNotInAndIsDeletedFalse")
                        && afterActiveCheck != null) afterActiveCheck.run();
                return result;
            });
            return proxy.getProxy();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GateConfiguration {
        @Bean
        static Gates concurrencyGates() { return new Gates(); }
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
