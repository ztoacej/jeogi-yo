package com.georgia.jeogiyo.order.service;

import com.georgia.jeogiyo.address.entity.Address;
import com.georgia.jeogiyo.address.repository.AddressRepository;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;
import com.georgia.jeogiyo.global.response.PageResponse;
import com.georgia.jeogiyo.global.util.DeliveryAreaValidator;
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
import com.georgia.jeogiyo.payment.entity.PaymentStatus;
import com.georgia.jeogiyo.payment.repository.PaymentRepository;
import com.georgia.jeogiyo.product.entity.Product;
import com.georgia.jeogiyo.product.repository.ProductRepository;
import com.georgia.jeogiyo.store.entity.QStore;
import com.georgia.jeogiyo.store.entity.Store;
import com.georgia.jeogiyo.store.entity.StoreStatus;
import com.georgia.jeogiyo.store.repository.StoreRepository;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.repository.UserRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private final PaymentRepository paymentRepository;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.ORDER_REQUESTED, Set.of(OrderStatus.ORDER_ACCEPTED, OrderStatus.ORDER_REJECTED),
            OrderStatus.ORDER_ACCEPTED, Set.of(OrderStatus.COOKING_COMPLETED),
            OrderStatus.COOKING_COMPLETED, Set.of(OrderStatus.DELIVERY_PICKED_UP),
            OrderStatus.DELIVERY_PICKED_UP, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.DELIVERY_COMPLETED),
            OrderStatus.DELIVERY_COMPLETED, Set.of(OrderStatus.ORDER_COMPLETED)
    );

    public OrderService(OrderRepository orderRepository, AddressRepository addressRepository, ProductRepository productRepository,
                        OrderItemRepository orderItemRepository, StoreRepository storeRepository, UserRepository userRepository,
                        JPAQueryFactory queryFactory, EntityManager entityManager, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.queryFactory = queryFactory;
        this.entityManager = entityManager;
        this.paymentRepository = paymentRepository;
    }

    public Order getOrder(UUID orderId) {
        return orderRepository.findByOrderIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_ORDER));
    }

    @Transactional
    public OrderCreateResponse createOrder(String loginId, OrderCreateRequest orderCreateRequest) {

        User user = userRepository.findByLoginIdAndIsDeletedFalse(loginId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_USER));

        if (user.getRole() != Role.CUSTOMER) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        Store store = storeRepository.findByStoreIdAndIsDeletedFalse(orderCreateRequest.getStoreId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_STORE));

        if (store.isDeleted()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND_STORE);
        }
        if (store.getStoreStatus() != StoreStatus.OPEN) {
            throw new BusinessException(GlobalErrorCode.STORE_NOT_OPEN);
        }

        Address address = addressRepository.findByUserAndAddressIdAndIsDeletedFalse(user, orderCreateRequest.getAddressId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.FORBIDDEN_ADDRESS));

        DeliveryAreaValidator.validate(address.getRoadAddress());

        Integer totalPrice = 0;

        // 재고 정합성 보장
        for (OrderCreateRequest.OrderItemRequest item : orderCreateRequest.getItems()) {

            if (item.getQuantity() <= 0) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }

            Product product = productRepository.findByProductIdAndIsDeletedFalse(item.getProductId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_PRODUCT));

            if (!product.getStore().getStoreId().equals(store.getStoreId())) {
                throw new BusinessException(GlobalErrorCode.PRODUCT_NOT_IN_STORE);
            }

            if (!product.isOrderable()) {
                throw new BusinessException(GlobalErrorCode.PRODUCT_NOT_ORDERABLE);
            }
            // 조건부 UPDATE로 재고 차감 성공 여부를 DB에서 최종 검증한다.
            int updatedRows = productRepository.decreaseStockIfEnough(
                    item.getProductId(),
                    store.getStoreId(),
                    item.getQuantity()
            );

            if (updatedRows != 1) {
                throw new BusinessException(GlobalErrorCode.INSUFFICIENT_STOCK);
            }

            Integer itemTotalPrice = product.getPrice() * item.getQuantity();
            totalPrice += itemTotalPrice;
        }

        Order order = new Order(user, store, address,
                address.getRoadAddress(), address.getDetailAddress(), address.getZipcode(),
                totalPrice, OrderStatus.ORDER_REQUESTED);
        Order savedOrder = orderRepository.save(order);

        for (OrderCreateRequest.OrderItemRequest item : orderCreateRequest.getItems()) {
            Product product = productRepository.findByProductIdAndIsDeletedFalse(item.getProductId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_PRODUCT));
            Integer itemTotalPrice = product.getPrice() * item.getQuantity();

            OrderItem orderItem = new OrderItem(savedOrder.getOrderId(), item.getProductId(), item.getQuantity(),
                    product.getPrice(), itemTotalPrice);
            orderItemRepository.save(orderItem);
        }

        OrderCreateResponse response = new OrderCreateResponse();
        response.setOrderId(savedOrder.getOrderId());
        response.setStoreId(savedOrder.getStore().getStoreId());
        response.setAddress(savedOrder.getRoadAddress() + " " + savedOrder.getDetailAddress());
        response.setOrderStatus(savedOrder.getOrderStatus().name());
        response.setTotalPrice(savedOrder.getTotalPrice());
        response.setCreatedAt(savedOrder.getCreatedAt());

        return response;
    }


    public OrderDetailResponse getOrderDetail(String loginId, UUID orderId) {

        User user = userRepository.findByLoginIdAndIsDeletedFalse(loginId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_USER));

        Order order = orderRepository.findByOrderIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_ORDER));

        validateOrderAccess(user, order);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        List<OrderDetailResponse.OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByProductIdAndIsDeletedFalse(item.getProductId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_PRODUCT));

            OrderDetailResponse.OrderItemResponse itemResponse = new OrderDetailResponse.OrderItemResponse();
            itemResponse.setProductId(item.getProductId());
            itemResponse.setProductName(product.getProductName());
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(item.getUnitPrice());
            itemResponse.setItemTotalPrice(item.getItemTotalPrice());
            itemResponses.add(itemResponse);
        }

        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId(order.getOrderId());
        response.setStoreId(order.getStore().getStoreId());
        response.setAddressId(order.getAddress().getAddressId());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setTotalPrice(order.getTotalPrice());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(itemResponses);

        return response;
    }

    private void validateOrderAccess(User user, Order order) {
        if (user.isMaster()) {
            return;
        }
        if (user.getRole() == Role.CUSTOMER) {
            if (!order.getUser().getUserId().equals(user.getUserId())) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
            }
            return;
        }
        if (user.isOwner()) {
            Store store = storeRepository.findByStoreIdAndIsDeletedFalse(order.getStore().getStoreId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_STORE));
            if (!store.getOwner().getUserId().equals(user.getUserId())) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
            }
            return;
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
    }

    public PageResponse<OrderSearchResponse> searchOrders(String loginId, OrderStatus orderStatus, Pageable pageable) {

        User user = userRepository.findByLoginIdAndIsDeletedFalse(loginId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_USER));

        List<UUID> storeIds = null;
        if (user.getRole() == Role.OWNER) {
            storeIds = queryFactory
                    .select(QStore.store.storeId)
                    .from(QStore.store)
                    .where(QStore.store.owner.userId.eq(user.getUserId())
                            .and(QStore.store.isDeleted.isFalse()))
                    .fetch();
        }

        Page<Order> orderPage = orderRepository.searchOrders(orderStatus, user.getRole(), user.getUserId(), storeIds, pageable);

        return PageResponse.from(orderPage, order -> {
            Store store = storeRepository.findByStoreIdAndIsDeletedFalse(order.getStore().getStoreId()).orElse(null);

            OrderSearchResponse item = new OrderSearchResponse();
            item.setOrderId(order.getOrderId());
            item.setStoreId(order.getStore().getStoreId());
            item.setStoreName(store != null ? store.getStoreName() : null);
            item.setOrderStatus(order.getOrderStatus().name());
            item.setTotalPrice(order.getTotalPrice());
            item.setCreatedAt(order.getCreatedAt());
            return item;
        });
    }

    public PageResponse<OrderStoreSearchResponse> searchOrdersByStore(String loginId, UUID storeId, OrderStatus orderStatus, Pageable pageable) {

        User user = userRepository.findByLoginIdAndIsDeletedFalse(loginId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_USER));

        Store store = storeRepository.findByStoreIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_STORE));

        if (user.getRole() == Role.OWNER) {
            if (!store.getOwner().getUserId().equals(user.getUserId())) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
            }
        } else if (user.getRole() != Role.MASTER) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        Page<Order> orderPage = orderRepository.searchOrdersByStore(storeId, orderStatus, pageable);

        return PageResponse.from(orderPage, order -> {
            User customer = userRepository.findByUserIdAndIsDeletedFalse(order.getUser().getUserId()).orElse(null);

            OrderStoreSearchResponse item = new OrderStoreSearchResponse();
            item.setOrderId(order.getOrderId());
            item.setCustomerName(customer != null ? customer.getNickname() : "탈퇴한 회원");
            item.setOrderStatus(order.getOrderStatus().name());
            item.setTotalPrice(order.getTotalPrice());
            item.setCreatedAt(order.getCreatedAt());
            return item;
        });
    }

    @Transactional
    public OrderStatusUpdateResponse updateOrderStatus(String loginId, UUID orderId, OrderStatusUpdateRequest request) {

        User user = userRepository.findByLoginIdAndIsDeletedFalse(loginId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_USER));

        if (user.getRole() != Role.OWNER && user.getRole() != Role.MASTER) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        Order order = orderRepository.findByOrderIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_ORDER));

        if (user.getRole() == Role.OWNER) {
            Store store = storeRepository.findByStoreIdAndIsDeletedFalse(order.getStore().getStoreId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_STORE));
            if (!store.getOwner().getUserId().equals(user.getUserId())) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
            }
        }

        OrderStatus currentStatus = order.getOrderStatus();
        OrderStatus nextStatus = request.getOrderStatus();

        Set<OrderStatus> allowedNext = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowedNext == null || !allowedNext.contains(nextStatus)) {
            throw new BusinessException(GlobalErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        if (nextStatus == OrderStatus.ORDER_ACCEPTED) {
            Payment payment = paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(orderId)
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NO_PAYMENT_FOUND_FOR_ORDER));
            if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_NOT_SUCCESS);
            }
        }

        order.changeStatus(nextStatus);
        entityManager.flush();

        OrderStatusUpdateResponse response = new OrderStatusUpdateResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setUpdatedAt(order.getUpdatedAt());

        return response;
    }

    @Transactional
    public OrderCancelResponse cancelOrder(String loginId, UUID orderId, OrderCancelRequest request) {

        User user = userRepository.findByLoginIdAndIsDeletedFalse(loginId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_USER));

        if (user.getRole() != Role.CUSTOMER && user.getRole() != Role.MASTER) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        Order order = orderRepository.findByOrderIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_ORDER));

        if (user.getRole() == Role.CUSTOMER) {
            if (!order.getUser().getUserId().equals(user.getUserId())) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
            }
            if (order.getOrderStatus() != OrderStatus.ORDER_REQUESTED) {
                throw new BusinessException(GlobalErrorCode.ORDER_ALREADY_ACCEPTED);
            }
            if (order.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
                throw new BusinessException(GlobalErrorCode.ORDER_CANCEL_TIME_EXPIRED);
            }
        } else {
            if (order.getOrderStatus() == OrderStatus.DELIVERED
                    || order.getOrderStatus() == OrderStatus.CANCELLED
                    || order.getOrderStatus() == OrderStatus.ORDER_REJECTED) {
                throw new BusinessException(GlobalErrorCode.ORDER_CANCEL_NOT_ALLOWED);
            }
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByProductIdAndIsDeletedFalse(item.getProductId()).orElse(null);
            if (product != null) {
                product.restoreStock(item.getQuantity());
            }
        }

        paymentRepository.findByOrder_OrderIdAndIsDeletedFalse(orderId).ifPresent(payment -> {
            if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                payment.cancel(request.getCancelReason());
            }
        });

        order.changeStatus(OrderStatus.CANCELLED);
        entityManager.flush();

        OrderCancelResponse response = new OrderCancelResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderStatus(order.getOrderStatus().name());
        response.setCanceledAt(order.getUpdatedAt());
        response.setCancelReason(request.getCancelReason());

        return response;
    }

    @Transactional
    public void cancelByPayment(UUID orderId, String loginId) {

        Order order = orderRepository.findByOrderIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_ORDER));

        if (order.getOrderStatus() != OrderStatus.ORDER_REQUESTED) {
            throw new BusinessException(GlobalErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : orderItems) {
            Product product = productRepository.findByProductIdAndIsDeletedFalse(item.getProductId()).orElse(null);
            if (product != null) {
                product.restoreStock(item.getQuantity());
            }
        }

        order.changeStatus(OrderStatus.CANCELLED);
    }
}