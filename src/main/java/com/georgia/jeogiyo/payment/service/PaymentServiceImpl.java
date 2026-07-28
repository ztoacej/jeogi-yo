package com.georgia.jeogiyo.payment.service;

import com.georgia.jeogiyo.global.response.PageResponse;
import com.georgia.jeogiyo.global.util.PageUtil;
import com.georgia.jeogiyo.order.entity.Order;
import com.georgia.jeogiyo.order.entity.OrderStatus;
import com.georgia.jeogiyo.order.repository.OrderRepository;
import com.georgia.jeogiyo.order.service.OrderService;
import com.georgia.jeogiyo.payment.dto.request.PaymentCancelRequest;
import com.georgia.jeogiyo.payment.dto.request.PaymentCreateRequest;
import com.georgia.jeogiyo.payment.dto.response.PaymentCancelResponse;
import com.georgia.jeogiyo.payment.dto.response.PaymentCreateResponse;
import com.georgia.jeogiyo.payment.dto.response.PaymentResponse;
import com.georgia.jeogiyo.payment.dto.response.PaymentSearchResponse;
import com.georgia.jeogiyo.payment.entity.Payment;
import com.georgia.jeogiyo.payment.entity.PaymentMethod;
import com.georgia.jeogiyo.payment.entity.PaymentStatus;
import com.georgia.jeogiyo.payment.repository.PaymentRepository;
import com.georgia.jeogiyo.user.entity.Role;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.service.UserFinder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserFinder userFinder;
    private final EntityManager entityManager;
    private final OrderService orderService;

    @Override
    public PaymentCreateResponse createPayment(UUID orderId, String loginId, PaymentCreateRequest request) {
        User user = userFinder.getUserByLoginId(loginId);
        validateCustomer(user);

        Order order = findOrderById(orderId);
        validateOrderOwner(order, user);
        validatePayableOrder(order);

        if (request.getPaymentMethod() != PaymentMethod.CARD) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 주문 1건에는 결제 1건만 허용한다. DB에서도 p_payment.order_id unique로 한 번 더 막는다.
        if (paymentRepository.existsByOrder_OrderId(orderId)) {
            throw new BusinessException(GlobalErrorCode.DUPLICATE_PAYMENT);
        }

        Payment payment = new Payment(
                order,
                user,
                request.getPaymentMethod(),
                order.getTotalPrice()
        );

        Payment saved = paymentRepository.save(payment);

        log.info("Payment created. paymentId={}, orderId={}, userId={}",
                saved.getPaymentId(), saved.getOrderId(), saved.getUserId());

        return toCreateResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, String loginId) {
        User user = userFinder.getUserByLoginId(loginId);
        Payment payment = findPaymentForRead(paymentId, user);

        validateReadable(user, payment);

        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentSearchResponse> searchPayments(
            PaymentStatus paymentStatus,
            int page,
            int size,
            String sort,
            String loginId
    ) {
        User user = userFinder.getUserByLoginId(loginId);
        validateCustomerOrMaster(user);

        Pageable pageable = PageUtil.toPageable(page, size, sort);

        // CUSTOMER는 본인 결제만, MASTER는 관리 목적상 전체 결제를 조회한다.
        boolean includeDeleted = user.getRole() == Role.MASTER;
        UUID userId = user.getRole() == Role.MASTER ? null : user.getUserId();

        Page<Payment> paymentPage =
                paymentRepository.searchPayments(paymentStatus, userId, includeDeleted, pageable);

        return PageResponse.from(paymentPage, this::toSearchResponse);
    }

    @Override
    public PaymentCancelResponse cancelPayment(UUID paymentId, String loginId, PaymentCancelRequest request) {
        User user = userFinder.getUserByLoginId(loginId);
        validateCustomerOrMaster(user);

        Payment payment = findPaymentById(paymentId);
        validateReadable(user, payment);

        Order order = findOrderById(payment.getOrderId());
        validateCancelableOrder(order);

        // 주문 취소 선점 → 재고 복구 → 결제 취소 순서로 처리한다.
        orderService.cancelByPayment(order.getOrderId(), loginId);
        payment.cancel(request.getCancelReason());
        entityManager.flush();

        log.info("Payment canceled. paymentId={}, orderId={}, canceledBy={}",
                payment.getPaymentId(), order.getOrderId(), loginId);

        return PaymentCancelResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPaymentStatus())
                .canceledAt(payment.getCanceledAt())
                .cancelReason(payment.getCancelReason())
                .build();
    }

    @Override
    public void deletePayment(UUID paymentId, String loginId) {
        userFinder.getMasterUserByLoginId(loginId);

        Payment payment = findPaymentById(paymentId);

        if (payment.getPaymentStatus() != PaymentStatus.CANCEL) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DELETE_NOT_ALLOWED);
        }

        payment.softDelete(loginId);

        log.info("Payment soft deleted. paymentId={}, deletedBy={}", payment.getPaymentId(), loginId);
    }

    private Payment findPaymentById(UUID paymentId) {
        return paymentRepository.findByPaymentIdAndIsDeletedFalse(paymentId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_PAYMENT));
    }

    private Payment findPaymentForRead(UUID paymentId, User user) {
        if (user.getRole() == Role.MASTER) {
            return paymentRepository.findByPaymentId(paymentId)
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_PAYMENT));
        }

        return findPaymentById(paymentId);
    }

    private Order findOrderById(UUID orderId) {
        return orderRepository.findByOrderIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND_ORDER));
    }

    private void validateOrderOwner(Order order, User user) {
        if (!order.getUserId().equals(user.getUserId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN_ORDER);
        }
    }

    // 현재 정책상 주문 요청 상태에서만 결제를 허용한다.
    // OrderStatus에 결제 완료/주문 취소 상태가 추가되면 조건을 재검토해야 한다.
    private void validatePayableOrder(Order order) {
        if (order.getOrderStatus() != OrderStatus.ORDER_REQUESTED) {
            throw new BusinessException(GlobalErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
    }

    // 조리/배송이 진행된 주문의 결제 취소를 막기 위한 주문 상태 검증이다.
    private void validateCancelableOrder(Order order) {
        if (order.getOrderStatus() != OrderStatus.ORDER_REQUESTED) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }
    }

    private void validateReadable(User user, Payment payment) {
        if (user.getRole() == Role.MASTER) {
            return;
        }

        if (user.getRole() != Role.CUSTOMER || !payment.isPaidBy(user.getUserId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN_PAYMENT);
        }
    }

    private void validateCustomer(User user) {
        if (user.getRole() != Role.CUSTOMER) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN_PAYMENT);
        }
    }

    private void validateCustomerOrMaster(User user) {
        if (user.getRole() != Role.CUSTOMER && user.getRole() != Role.MASTER) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN_PAYMENT);
        }
    }

    private PaymentCreateResponse toCreateResponse(Payment payment) {
        return PaymentCreateResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .approvedAt(payment.getApprovedAt())
                .build();
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .approvedAt(payment.getApprovedAt())
                .canceledAt(payment.getCanceledAt())
                .cancelReason(payment.getCancelReason())
                .build();
    }

    private PaymentSearchResponse toSearchResponse(Payment payment) {
        return PaymentSearchResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrderId())
                .paymentMethod(payment.getPaymentMethod())
                .paymentStatus(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .approvedAt(payment.getApprovedAt())
                .build();
    }
}