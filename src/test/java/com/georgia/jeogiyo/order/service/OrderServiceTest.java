package com.georgia.jeogiyo.order.service;

import com.georgia.jeogiyo.address.dto.request.AddressCreateRequest;
import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.category.entity.Category;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.response.PageResponse;
import com.georgia.jeogiyo.global.util.DeliveryAreaValidator;
import com.georgia.jeogiyo.global.util.PageUtil;
import com.georgia.jeogiyo.order.dto.request.OrderCancelRequest;
import com.georgia.jeogiyo.order.dto.request.OrderCreateRequest;
import com.georgia.jeogiyo.order.dto.request.OrderStatusUpdateRequest;
import com.georgia.jeogiyo.order.dto.response.*;
import com.georgia.jeogiyo.order.entity.Order;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.order.repository.OrderRepository;
import com.georgia.jeogiyo.orderitem.entity.OrderItem;
import com.georgia.jeogiyo.orderitem.repository.OrderItemRepository;
import com.georgia.jeogiyo.payment.entity.Payment;
import com.georgia.jeogiyo.payment.entity.PaymentMethod;
import com.georgia.jeogiyo.payment.entity.PaymentStatus;
import com.georgia.jeogiyo.payment.repository.PaymentRepository;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.georgia.jeogiyo.support.DomainTestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID ADDRESS_ID = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID ORDER_ID = UUID.fromString("77777777-7777-7777-7777-777777777771");

    @Mock private OrderRepository orderRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private JPAQueryFactory queryFactory;
    @Mock private EntityManager entityManager;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DeliveryAreaValidator deliveryAreaValidator;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository, addressRepository, productRepository,
                orderItemRepository, storeRepository, userRepository,
                queryFactory, entityManager, paymentRepository, deliveryAreaValidator
        );
    }

    // ---------- fixture 헬퍼 ----------

    private Address address(User owner, UUID addressId, String roadAddress) {
        AddressCreateRequest request = new AddressCreateRequest();
        ReflectionTestUtils.setField(request, "roadAddress", roadAddress);
        ReflectionTestUtils.setField(request, "detailAddress", "상세주소");
        ReflectionTestUtils.setField(request, "zipcode", "03150");
        ReflectionTestUtils.setField(request, "isDefault", true);

        Address address = Address.create(owner, request);
        ReflectionTestUtils.setField(address, "addressId", addressId);
        return address;
    }

    private Payment payment(PaymentStatus status) {
        Order order = order(customer(), store(owner(), category()), address(customer(), ADDRESS_ID, "서울특별시 종로구 세종대로 172"),
                ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        Payment payment = new Payment(order, customer(), PaymentMethod.CARD, 24000);
        ReflectionTestUtils.setField(payment, "paymentStatus", status);
        return payment;
    }

    private Product product(Store store, Category category, int price, int stock, boolean hidden) {
        Product product = new Product(store, category, "테스트 상품", "설명", price, stock, hidden);
        ReflectionTestUtils.setField(product, "productId", PRODUCT_ID);
        return product;
    }

    private OrderCreateRequest orderRequest(UUID storeId, UUID addressId, UUID productId, int quantity) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setStoreId(storeId);
        request.setAddressId(addressId);

        OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));

        return request;
    }

    private Order order(User user, Store store, Address address, UUID orderId, OrderStatus status, Integer totalPrice) {
        return order(user, store, address, orderId, status, totalPrice, LocalDateTime.now());
    }

    private Order order(User user, Store store, Address address, UUID orderId, OrderStatus status, Integer totalPrice, LocalDateTime createdAt) {
        Order order = new Order(user, store, address, address.getRoadAddress(), address.getDetailAddress(), address.getZipcode(), totalPrice, status);
        ReflectionTestUtils.setField(order, "orderId", orderId);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        ReflectionTestUtils.setField(order, "updatedAt", createdAt);
        return order;
    }

    private OrderItem orderItem(UUID orderId, UUID productId, Integer quantity, Integer unitPrice, Integer itemTotalPrice) {
        return new OrderItem(orderId, productId, "테스트 상품", quantity, unitPrice, itemTotalPrice);
    }

    private OrderSearchResponse orderSearchResponse() {
        OrderSearchResponse response = new OrderSearchResponse();
        response.setOrderId(ORDER_ID);
        response.setStoreId(STORE_ID);
        response.setStoreName("테스트 가게");
        response.setOrderStatus("ORDER_REQUESTED");
        response.setTotalPrice(24000);
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    private OrderStoreSearchResponse orderStoreSearchResponse() {
        OrderStoreSearchResponse response = new OrderStoreSearchResponse();
        response.setOrderId(ORDER_ID);
        response.setCustomerName("고객");
        response.setOrderStatus("ORDER_REQUESTED");
        response.setTotalPrice(24000);
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    // ---------- 6-1: 주문 생성 ----------

    @Test
    @DisplayName("CUSTOMER는 정상적으로 주문을 생성할 수 있다")
    void createOrder_success() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        store.changeStatus(StoreStatus.OPEN);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Product product = product(store, category, 12000, 30, false);
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(addressRepository.findByUserAndAddressIdAndIsDeletedFalse(any(User.class), eq(ADDRESS_ID))).willReturn(Optional.of(address));
        given(productRepository.findAllByProductIdInAndIsDeletedFalse(List.of(PRODUCT_ID)))
                .willReturn(List.of(product));
        given(productRepository.decreaseStockIfEnough(eq(PRODUCT_ID), eq(STORE_ID), anyInt()))
                .willReturn(1);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
            return order;
        });

        OrderCreateResponse response = orderService.createOrder(CUSTOMER_LOGIN_ID, request);

        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getTotalPrice()).isEqualTo(24000);
        assertThat(response.getOrderStatus()).isEqualTo("ORDER_REQUESTED");

        // 조건부 UPDATE 방식이므로 엔티티 stock 변경 대신 repository 호출 여부를 검증한다.
        then(productRepository).should().decreaseStockIfEnough(PRODUCT_ID, STORE_ID, 2);
        then(orderItemRepository).should().save(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 예외가 발생한다")
    void createOrder_userNotFound_fail() {
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);
        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }

    @Test
    @DisplayName("존재하지 않는 가게로는 주문할 수 없다")
    void createOrder_storeNotFound_fail() {
        User customer = customer();
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 가게");
    }

    @Test
    @DisplayName("영업 중(OPEN)이 아닌 가게에는 주문할 수 없다")
    void createOrder_storeNotOpen_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("영업중인 가게");
    }

    @Test
    @DisplayName("본인 소유가 아닌 배송지로는 주문할 수 없다")
    void createOrder_addressNotOwned_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        store.changeStatus(StoreStatus.OPEN);
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(addressRepository.findByUserAndAddressIdAndIsDeletedFalse(any(User.class), eq(ADDRESS_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("본인의 배송지");
    }

    @Test
    @DisplayName("서비스 가능 지역이 아닌 배송지로는 주문할 수 없다")
    void createOrder_addressNotServiceable_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        store.changeStatus(StoreStatus.OPEN);
        Address farAddress = address(customer, ADDRESS_ID, "부산시 해운대구 어딘가로 1");
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(addressRepository.findByUserAndAddressIdAndIsDeletedFalse(any(User.class), eq(ADDRESS_ID))).willReturn(Optional.of(farAddress));

        willThrow(new BusinessException(GlobalErrorCode.OUT_OF_SERVICE_AREA))
                .given(deliveryAreaValidator)
                .validate(farAddress.getRoadAddress());

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("서비스 가능 지역");
    }

    @Test
    @DisplayName("요청한 가게에 속하지 않은 상품은 주문할 수 없다")
    void createOrder_productNotInStore_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        store.changeStatus(StoreStatus.OPEN);
        Store otherStore = otherOwnerStore(otherOwner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Product productFromOtherStore = product(otherStore, category, 12000, 30, false);
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(addressRepository.findByUserAndAddressIdAndIsDeletedFalse(any(User.class), eq(ADDRESS_ID))).willReturn(Optional.of(address));
        given(productRepository.findAllByProductIdInAndIsDeletedFalse(List.of(PRODUCT_ID))).willReturn(List.of(productFromOtherStore));
        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("속하지 않은 상품");
    }

    @Test
    @DisplayName("숨김 상품은 주문할 수 없다")
    void createOrder_hiddenProduct_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        store.changeStatus(StoreStatus.OPEN);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Product hiddenProduct = product(store, category, 12000, 30, true);
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 2);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(addressRepository.findByUserAndAddressIdAndIsDeletedFalse(any(User.class), eq(ADDRESS_ID))).willReturn(Optional.of(address));
        given(productRepository.findAllByProductIdInAndIsDeletedFalse(List.of(PRODUCT_ID))).willReturn(List.of(hiddenProduct));

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("주문할 수 없는 상품");
    }

    @Test
    @DisplayName("재고보다 많은 수량을 주문하면 예외가 발생한다")
    void createOrder_insufficientStock_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        store.changeStatus(StoreStatus.OPEN);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Product lowStockProduct = product(store, category, 12000, 3, false);
        OrderCreateRequest request = orderRequest(STORE_ID, ADDRESS_ID, PRODUCT_ID, 5);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(addressRepository.findByUserAndAddressIdAndIsDeletedFalse(any(User.class), eq(ADDRESS_ID))).willReturn(Optional.of(address));
        given(productRepository.findAllByProductIdInAndIsDeletedFalse(List.of(PRODUCT_ID)))
                .willReturn(List.of(lowStockProduct));

        given(productRepository.decreaseStockIfEnough(PRODUCT_ID, STORE_ID, 5))
                .willReturn(0);

        assertThatThrownBy(() -> orderService.createOrder(CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("재고가 부족");
    }

    // ---------- 6-2: 주문 상세 조회 ----------

    @Test
    @DisplayName("CUSTOMER는 본인 주문을 상세 조회할 수 있다")
    void getOrderDetail_customer_own_success() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);
        OrderItem orderItem = orderItem(ORDER_ID, PRODUCT_ID, 2, 12000, 24000);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderItemRepository.findByOrderId(ORDER_ID)).willReturn(List.of(orderItem));

        OrderDetailResponse response = orderService.getOrderDetail(CUSTOMER_LOGIN_ID, ORDER_ID);

        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("테스트 상품");
    }

    @Test
    @DisplayName("CUSTOMER는 본인 것이 아닌 주문을 조회할 수 없다")
    void getOrderDetail_customer_others_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(otherOwner(), ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order othersOrder = order(otherOwner(), store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(othersOrder));

        assertThatThrownBy(() -> orderService.getOrderDetail(CUSTOMER_LOGIN_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("주문에 접근할 권한");
    }

    @Test
    @DisplayName("OWNER는 본인 가게의 주문을 상세 조회할 수 있다")
    void getOrderDetail_owner_ownStore_success() {
        User owner = owner();
        User customer = customer();
        Category category = category();
        Store store = store(owner, category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);
        OrderItem orderItem = orderItem(ORDER_ID, PRODUCT_ID, 2, 12000, 24000);

        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(orderItemRepository.findByOrderId(ORDER_ID)).willReturn(List.of(orderItem));

        OrderDetailResponse response = orderService.getOrderDetail(OWNER_LOGIN_ID, ORDER_ID);

        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("OWNER는 본인 가게가 아닌 주문을 조회할 수 없다")
    void getOrderDetail_owner_otherStore_fail() {
        User owner = owner();
        User customer = customer();
        User otherOwner = otherOwner();
        Category category = category();
        Store otherStore = otherOwnerStore(otherOwner, category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, otherStore, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(OTHER_OWNER_STORE_ID)).willReturn(Optional.of(otherStore));

        assertThatThrownBy(() -> orderService.getOrderDetail(OWNER_LOGIN_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("주문에 접근할 권한");
    }

    @Test
    @DisplayName("MASTER는 모든 주문을 조회할 수 있다")
    void getOrderDetail_master_success() {
        User master = master();
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);
        OrderItem orderItem = orderItem(ORDER_ID, PRODUCT_ID, 2, 12000, 24000);

        given(userRepository.findByLoginIdAndIsDeletedFalse(MASTER_LOGIN_ID)).willReturn(Optional.of(master));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderItemRepository.findByOrderId(ORDER_ID)).willReturn(List.of(orderItem));

        OrderDetailResponse response = orderService.getOrderDetail(MASTER_LOGIN_ID, ORDER_ID);

        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 주문은 조회할 수 없다")
    void getOrderDetail_orderNotFound_fail() {
        User customer = customer();
        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetail(CUSTOMER_LOGIN_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 주문");
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 주문을 조회할 수 없다")
    void getOrderDetail_userNotFound_fail() {
        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetail(CUSTOMER_LOGIN_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }

    // ---------- 6-3: 주문 목록 조회 ----------

    @Test
    @DisplayName("CUSTOMER는 본인 주문 목록을 조회할 수 있다")
    void searchOrders_customer_success() {
        User customer = customer();
        OrderSearchResponse item = orderSearchResponse();
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.searchOrders(isNull(), eq(Role.CUSTOMER), eq(CUSTOMER_ID), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(item)));

        PageResponse<OrderSearchResponse> response = orderService.searchOrders(CUSTOMER_LOGIN_ID, null, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("MASTER는 전체 주문 목록을 조회할 수 있다")
    void searchOrders_master_success() {
        User master = master();
        OrderSearchResponse item = orderSearchResponse();
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");

        given(userRepository.findByLoginIdAndIsDeletedFalse(MASTER_LOGIN_ID)).willReturn(Optional.of(master));
        given(orderRepository.searchOrders(isNull(), eq(Role.MASTER), eq(MASTER_ID), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(item)));

        PageResponse<OrderSearchResponse> response = orderService.searchOrders(MASTER_LOGIN_ID, null, pageable);

        assertThat(response.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("잘못된 size는 PageUtil에서 10으로 보정되어 repository에 전달된다")
    void searchOrders_invalidSize_normalized() {
        User customer = customer();
        Pageable pageable = PageUtil.toPageable(0, 20, "desc");

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.searchOrders(isNull(), eq(Role.CUSTOMER), eq(CUSTOMER_ID), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<OrderSearchResponse>(List.of()));

        orderService.searchOrders(CUSTOMER_LOGIN_ID, null, pageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(orderRepository).should().searchOrders(isNull(), eq(Role.CUSTOMER), eq(CUSTOMER_ID), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 목록을 조회할 수 없다")
    void searchOrders_userNotFound_fail() {
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");
        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.searchOrders(CUSTOMER_LOGIN_ID, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }

    // ---------- 6-4: 가게별 주문 목록 조회 ----------

    @Test
    @DisplayName("OWNER는 본인 가게의 주문 목록을 조회할 수 있다")
    void searchOrdersByStore_owner_success() {
        User owner = owner();
        Category category = category();
        Store store = store(owner, category);
        OrderStoreSearchResponse item = orderStoreSearchResponse();
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");

        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(orderRepository.searchOrdersByStore(eq(STORE_ID), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(item)));

        PageResponse<OrderStoreSearchResponse> response = orderService.searchOrdersByStore(OWNER_LOGIN_ID, STORE_ID, null, pageable);

        assertThat(response.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("OWNER는 본인 가게가 아니면 목록을 조회할 수 없다")
    void searchOrdersByStore_owner_otherStore_fail() {
        User owner = owner();
        User otherOwner = otherOwner();
        Category category = category();
        Store otherStore = otherOwnerStore(otherOwner, category);
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");

        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(OTHER_OWNER_STORE_ID)).willReturn(Optional.of(otherStore));

        assertThatThrownBy(() -> orderService.searchOrdersByStore(OWNER_LOGIN_ID, OTHER_OWNER_STORE_ID, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("주문에 접근할 권한");
    }

    @Test
    @DisplayName("존재하지 않는 가게는 조회할 수 없다")
    void searchOrdersByStore_storeNotFound_fail() {
        User owner = owner();
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");
        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.searchOrdersByStore(OWNER_LOGIN_ID, STORE_ID, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 가게");
    }

    @Test
    @DisplayName("MASTER는 모든 가게의 주문 목록을 조회할 수 있다")
    void searchOrdersByStore_master_success() {
        User master = master();
        Category category = category();
        Store store = store(owner(), category);
        OrderStoreSearchResponse item = orderStoreSearchResponse();
        Pageable pageable = PageUtil.toPageable(0, 10, "desc");

        given(userRepository.findByLoginIdAndIsDeletedFalse(MASTER_LOGIN_ID)).willReturn(Optional.of(master));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));
        given(orderRepository.searchOrdersByStore(eq(STORE_ID), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(item)));

        PageResponse<OrderStoreSearchResponse> response = orderService.searchOrdersByStore(MASTER_LOGIN_ID, STORE_ID, null, pageable);

        assertThat(response.getContent()).hasSize(1);
    }

    // ---------- 6-5: 주문 상태 변경 ----------

    @Test
    @DisplayName("OWNER는 본인 가게 주문을 수락 상태로 변경할 수 있다")
    void updateOrderStatus_owner_accept_success() {
        User owner = owner();
        User customer = customer();
        Category category = category();
        Store store = store(owner, category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.ORDER_ACCEPTED);

        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(STORE_ID)).willReturn(Optional.of(store));

        Payment payment = payment(PaymentStatus.SUCCESS);
        given(paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(payment));
        given(orderRepository.updateStatusIfCurrent(
                ORDER_ID,
                OrderStatus.ORDER_REQUESTED,
                OrderStatus.ORDER_ACCEPTED
        )).willReturn(1);

        OrderStatusUpdateResponse response = orderService.updateOrderStatus(OWNER_LOGIN_ID, ORDER_ID, request);

        assertThat(response.getOrderStatus()).isEqualTo("ORDER_ACCEPTED");
    }

    @Test
    @DisplayName("MASTER는 아무 주문의 상태나 변경할 수 있다")
    void updateOrderStatus_master_success() {
        User master = master();
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.ORDER_REJECTED);

        given(userRepository.findByLoginIdAndIsDeletedFalse(MASTER_LOGIN_ID)).willReturn(Optional.of(master));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.updateStatusIfCurrent(
                ORDER_ID,
                OrderStatus.ORDER_REQUESTED,
                OrderStatus.ORDER_REJECTED
        )).willReturn(1);

        OrderStatusUpdateResponse response = orderService.updateOrderStatus(MASTER_LOGIN_ID, ORDER_ID, request);

        assertThat(response.getOrderStatus()).isEqualTo("ORDER_REJECTED");
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이는 실패한다")
    void updateOrderStatus_invalidTransition_fail() {
        User master = master();
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.DELIVERED);

        given(userRepository.findByLoginIdAndIsDeletedFalse(MASTER_LOGIN_ID)).willReturn(Optional.of(master));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(MASTER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("허용되지 않은 주문 상태");
    }

    @Test
    @DisplayName("OWNER는 본인 가게가 아닌 주문 상태를 변경할 수 없다")
    void updateOrderStatus_owner_otherStore_fail() {
        User owner = owner();
        User customer = customer();
        User otherOwner = otherOwner();
        Category category = category();
        Store otherStore = otherOwnerStore(otherOwner, category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, otherStore, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.ORDER_ACCEPTED);

        given(userRepository.findByLoginIdAndIsDeletedFalse(OWNER_LOGIN_ID)).willReturn(Optional.of(owner));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(storeRepository.findByStoreIdAndIsDeletedFalse(OTHER_OWNER_STORE_ID)).willReturn(Optional.of(otherStore));

        assertThatThrownBy(() -> orderService.updateOrderStatus(OWNER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("주문에 접근할 권한");
    }

    @Test
    @DisplayName("CUSTOMER는 주문 상태를 변경할 수 없다")
    void updateOrderStatus_customer_forbidden_fail() {
        User customer = customer();
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.ORDER_ACCEPTED);

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));

        assertThatThrownBy(() -> orderService.updateOrderStatus(CUSTOMER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("권한이 없습니다");
    }

    // ---------- 6-6: 주문 취소 ----------

    @Test
    @DisplayName("CUSTOMER는 주문 요청 5분 이내에 본인 주문을 취소할 수 있다")
    void cancelOrder_customer_success() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);
        OrderItem orderItem = orderItem(ORDER_ID, PRODUCT_ID, 2, 12000, 24000);
        Payment payment = payment(PaymentStatus.SUCCESS);

        OrderCancelRequest request = new OrderCancelRequest();
        request.setCancelReason("고객 변심");

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.updateStatusIfCurrent(ORDER_ID, OrderStatus.ORDER_REQUESTED, OrderStatus.CANCELLED)).willReturn(1);
        given(orderItemRepository.findByOrderId(ORDER_ID)).willReturn(List.of(orderItem));
        given(productRepository.increaseStock(PRODUCT_ID, 2)).willReturn(1);
        given(paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(payment));

        OrderCancelResponse response = orderService.cancelOrder(CUSTOMER_LOGIN_ID, ORDER_ID, request);
        then(productRepository).should().increaseStock(PRODUCT_ID, 2);

        assertThat(response.getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCEL);
        assertThat(payment.getCancelReason()).isEqualTo("고객 변심");
    }

    @Test
    @DisplayName("주문 취소는 이미 다른 요청이 취소한 주문이면 재고를 복구하지 않는다")
    void cancelOrder_alreadyCanceledByOtherRequest_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        OrderCancelRequest request = new OrderCancelRequest();
        request.setCancelReason("중복 취소 요청");

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.updateStatusIfCurrent(
                ORDER_ID,
                OrderStatus.ORDER_REQUESTED,
                OrderStatus.CANCELLED
        )).willReturn(0);

        assertThatThrownBy(() -> orderService.cancelOrder(CUSTOMER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 수 없는 상태");

        then(orderItemRepository).shouldHaveNoInteractions();
        then(productRepository).shouldHaveNoInteractions();
        then(paymentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("OWNER가 수락한 이후에는 CUSTOMER가 취소할 수 없다")
    void cancelOrder_customer_afterAccepted_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_ACCEPTED, 24000);

        OrderCancelRequest request = new OrderCancelRequest();

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(CUSTOMER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 수락된 주문");
    }

    @Test
    @DisplayName("주문 후 5분이 지나면 CUSTOMER가 취소할 수 없다")
    void cancelOrder_customer_expired_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000,
                LocalDateTime.now().minusMinutes(10));

        OrderCancelRequest request = new OrderCancelRequest();

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(CUSTOMER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5분이 지나");
    }

    @Test
    @DisplayName("CUSTOMER는 본인 것이 아닌 주문을 취소할 수 없다")
    void cancelOrder_customer_notOwn_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(otherOwner(), ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order othersOrder = order(otherOwner(), store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        OrderCancelRequest request = new OrderCancelRequest();

        given(userRepository.findByLoginIdAndIsDeletedFalse(CUSTOMER_LOGIN_ID)).willReturn(Optional.of(customer));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(othersOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(CUSTOMER_LOGIN_ID, ORDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("주문에 접근할 권한");
    }

    @Test
    @DisplayName("MASTER는 배송완료/취소/거절 상태가 아니면 주문을 취소할 수 있다")
    void cancelOrder_master_success() {
        User master = master();
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_ACCEPTED, 24000);
        OrderItem orderItem = orderItem(ORDER_ID, PRODUCT_ID, 2, 12000, 24000);

        OrderCancelRequest request = new OrderCancelRequest();

        given(userRepository.findByLoginIdAndIsDeletedFalse(MASTER_LOGIN_ID)).willReturn(Optional.of(master));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.updateStatusIfCurrent(ORDER_ID, OrderStatus.ORDER_ACCEPTED, OrderStatus.CANCELLED)).willReturn(1);
        given(orderItemRepository.findByOrderId(ORDER_ID)).willReturn(List.of(orderItem));
        given(productRepository.increaseStock(PRODUCT_ID, 2)).willReturn(1);

        OrderCancelResponse response = orderService.cancelOrder(MASTER_LOGIN_ID, ORDER_ID, request);
        then(productRepository).should().increaseStock(PRODUCT_ID, 2);

        assertThat(response.getOrderStatus()).isEqualTo("CANCELLED");
    }

    // ---------- cancelByPayment ----------

    @Test
    @DisplayName("cancelByPayment는 ORDER_REQUESTED 상태 주문을 취소하고 재고를 복구한다")
    void cancelByPayment_success() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);
        OrderItem orderItem = orderItem(ORDER_ID, PRODUCT_ID, 2, 12000, 24000);

        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.updateStatusIfCurrent(ORDER_ID, OrderStatus.ORDER_REQUESTED, OrderStatus.CANCELLED)).willReturn(1);
        given(orderItemRepository.findByOrderId(ORDER_ID)).willReturn(List.of(orderItem));
        given(productRepository.increaseStock(PRODUCT_ID, 2)).willReturn(1);

        orderService.cancelByPayment(ORDER_ID, CUSTOMER_LOGIN_ID);
        then(productRepository).should().increaseStock(PRODUCT_ID, 2);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

    }

    @Test
    @DisplayName("cancelByPayment는 이미 다른 요청이 취소한 주문이면 재고를 복구하지 않는다")
    void cancelByPayment_alreadyCanceledByOtherRequest_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_REQUESTED, 24000);

        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.updateStatusIfCurrent(
                ORDER_ID,
                OrderStatus.ORDER_REQUESTED,
                OrderStatus.CANCELLED
        )).willReturn(0);

        assertThatThrownBy(() -> orderService.cancelByPayment(ORDER_ID, CUSTOMER_LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 수 없는 상태");

        then(orderItemRepository).shouldHaveNoInteractions();
        then(productRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("cancelByPayment는 ORDER_REQUESTED가 아니면 실패한다")
    void cancelByPayment_notRequested_fail() {
        User customer = customer();
        Category category = category();
        Store store = store(owner(), category);
        Address address = address(customer, ADDRESS_ID, "서울특별시 종로구 세종대로 172");
        Order order = order(customer, store, address, ORDER_ID, OrderStatus.ORDER_ACCEPTED, 24000);

        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByPayment(ORDER_ID, CUSTOMER_LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("취소할 수 없는 상태");
    }
}