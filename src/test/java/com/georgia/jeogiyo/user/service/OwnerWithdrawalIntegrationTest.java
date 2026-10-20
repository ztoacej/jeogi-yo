package com.georgia.jeogiyo.user.service;

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
import com.georgia.jeogiyo.order.dto.request.OrderCancelRequest;
import com.georgia.jeogiyo.payment.service.PaymentService;
import com.georgia.jeogiyo.payment.repository.PaymentRepository;
import com.georgia.jeogiyo.store.service.StoreService;
import com.georgia.jeogiyo.support.DomainTestFixture;
import com.georgia.jeogiyo.payment.entity.PaymentMethod;
import com.georgia.jeogiyo.user.dto.request.UserDeleteRequest;
import com.georgia.jeogiyo.global.jwt.JwtUtil;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:owner-withdrawal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "SECURITY_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "gemini.api.key=test"
})
@AutoConfigureMockMvc
class OwnerWithdrawalIntegrationTest {
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private UserService userService;
    @Autowired private StoreService storeService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbcTemplate;
    private User owner;
    private User customer;
    private Store store;
    private Category category;
    private Address address;

    @BeforeEach
    void setUp() {
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
    @DisplayName("폐업 가게 OWNER의 탈퇴는 진행 중 주문이 있으면 거절하고 종료된 주문만 있으면 허용한다")
    void withdrawalChecksAllOrderStatuses(OrderStatus orderStatus) throws Exception {
        UUID id = paidOrder(store);
        advance(id, orderStatus);
        close(store);
        var paymentBefore = paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(id).orElseThrow().getPaymentStatus();
        boolean terminal = List.of(OrderStatus.ORDER_COMPLETED, OrderStatus.CANCELLED, OrderStatus.ORDER_REJECTED).contains(orderStatus);
        if (terminal) {
            withdrawRequest(200);
        } else {
            withdrawRequest(409);
        }
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isEqualTo(terminal);
        assertThat(orderRepository.findById(id).orElseThrow().getOrderStatus()).isEqualTo(orderStatus);
        assertThat(paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(id).orElseThrow().getPaymentStatus()).isEqualTo(paymentBefore);
    }

    @Test
    @DisplayName("주문이 없는 폐업 가게만 있는 OWNER는 기존처럼 탈퇴할 수 있다")
    void noOrders_withdrawalAllowed() throws Exception {
        close(store);
        withdrawRequest(200);
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StoreStatus.class, names = {"OPEN", "CLOSED"})
    @DisplayName("진행 중 주문이 없어도 OPEN·CLOSED 가게가 있으면 기존 정책대로 탈퇴를 거절한다")
    void activeStore_stillBlocksWithdrawal(StoreStatus storeStatus) {
        storeService.updateStoreStatus(store.getStoreId(), owner.getLoginId(), DomainTestFixture.storeStatusRequest(storeStatus));
        assertThatThrownBy(() -> withdraw())
                .isInstanceOf(BusinessException.class).hasMessage(GlobalErrorCode.DELETE_FAILURE_OPEN_STORES.getMessage());
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("다른 OWNER의 진행 중 주문은 탈퇴를 막지 않는다")
    void otherOwnersOrders_doNotBlockWithdrawal() throws Exception {
        User other = userRepository.save(user("o" + UUID.randomUUID().toString().substring(0, 8), Role.OWNER));
        Store otherStore = newStore(other);
        UUID otherOrder = paidOrder(otherStore);
        close(store);
        withdrawRequest(200);
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isTrue();
        assertThat(userRepository.findById(other.getUserId()).orElseThrow().isDeleted()).isFalse();
        assertThat(orderRepository.findById(otherOrder).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ORDER_REQUESTED);
    }

    @Test
    @DisplayName("여러 폐업 가게 중 한 곳에라도 진행 중 주문이 있으면 탈퇴를 막는다")
    void checksAllOwnedStores() throws Exception {
        UUID first = paidOrder(store);
        advance(first, OrderStatus.CANCELLED);
        close(store);
        Store second = newStore(owner);
        UUID pending = paidOrder(second);
        close(second);
        withdrawRequest(409);
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isFalse();
        assertThat(orderRepository.findById(pending).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ORDER_REQUESTED);
    }

    @Test
    @DisplayName("기존 삭제 가게에 남은 진행 중 주문도 탈퇴 검증에서 누락하지 않는다")
    void legacyDeletedStoreOrder_blocksWithdrawal() throws Exception {
        paidOrder(store);
        // 이전 버전에서 가게가 먼저 삭제된 데이터를 재현한다.
        jdbcTemplate.update("update p_store set is_deleted = true where store_id = ?", store.getStoreId());
        withdrawRequest(409);
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 거절 후 사장이 남은 주문을 처리하면 다시 탈퇴할 수 있다")
    void finishPendingOrder_thenRetryWithdrawal() throws Exception {
        UUID id = paidOrder(store);
        close(store);
        withdrawRequest(409);
        advance(id, OrderStatus.ORDER_REJECTED);
        withdrawRequest(200);
        assertThat(userRepository.findById(owner.getUserId()).orElseThrow().isDeleted()).isTrue();
        assertThat(orderRepository.findById(id).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ORDER_REJECTED);
    }

    private void withdrawRequest(int expectedStatus) throws Exception {
        var result = mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", jwtUtil.createToken(owner.getLoginId(), Role.OWNER))
                        .contentType("application/json")
                        .content("{\"email\":\"" + owner.getEmail() + "\",\"password\":\"Test1234!\"}"))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 409) {
            result.andExpect(jsonPath("$.message").value(GlobalErrorCode.DELETE_FAILURE_ACTIVE_ORDERS.getMessage()));
        }
    }

    private void withdraw() {
        userService.delete(owner.getLoginId(), new UserDeleteRequest(owner.getEmail(), "Test1234!"));
    }

    private UUID paidOrder(Store target) {
        Product product = productRepository.save(new Product(target, category, "테스트 상품", "설명", 12000, 1, false));
        OrderCreateRequest request = request(List.of(item(product, 1)));
        request.setStoreId(target.getStoreId());
        UUID id = orderService.createOrder(customer.getLoginId(), request).getOrderId();
        paymentService.createPayment(id, customer.getLoginId(), DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD));
        return id;
    }

    private void close(Store target) {
        storeService.updateStoreStatus(target.getStoreId(), owner.getLoginId(), DomainTestFixture.storeStatusRequest(StoreStatus.OUT_OF_BUSINESS));
    }

    private Store newStore(User storeOwner) {
        Store value = new Store(storeOwner, category, "추가 가게", "서울특별시 종로구 세종대로 172", "02-0000-0000");
        value.changeStatus(StoreStatus.OPEN);
        return storeRepository.save(value);
    }

    private void advance(UUID id, OrderStatus target) {
        if (target == OrderStatus.ORDER_REQUESTED) return;
        if (target == OrderStatus.CANCELLED) {
            OrderCancelRequest request = new OrderCancelRequest();
            request.setCancelReason("고객 취소");
            orderService.cancelOrder(customer.getLoginId(), id, request);
            return;
        }
        if (target == OrderStatus.ORDER_REJECTED) {
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
            request.setOrderStatus(target);
            orderService.updateOrderStatus(owner.getLoginId(), id, request);
            return;
        }
        for (OrderStatus next : List.of(OrderStatus.ORDER_ACCEPTED, OrderStatus.COOKING_COMPLETED,
                OrderStatus.DELIVERY_PICKED_UP, OrderStatus.DELIVERED, OrderStatus.DELIVERY_COMPLETED, OrderStatus.ORDER_COMPLETED)) {
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
            request.setOrderStatus(next);
            orderService.updateOrderStatus(owner.getLoginId(), id, request);
            if (next == target) return;
        }
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
