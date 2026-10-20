package com.georgia.jeogiyo.payment.service;

import com.georgia.jeogiyo.global.response.PageResponse;
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
import com.georgia.jeogiyo.support.DomainTestFixture;
import com.georgia.jeogiyo.user.entity.User;
import com.georgia.jeogiyo.user.exception.UserDomainException;
import com.georgia.jeogiyo.user.exception.UserErrorCode;
import com.georgia.jeogiyo.user.service.UserFinder;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.georgia.jeogiyo.support.DomainTestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import com.georgia.jeogiyo.global.exception.BusinessException;
import com.georgia.jeogiyo.global.exception.GlobalErrorCode;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID OTHER_CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111115");

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserFinder userFinder;
    @Mock private EntityManager entityManager;
    @Mock private OrderService orderService;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                orderRepository,
                userFinder,
                entityManager,
                orderService
        );
    }

    @Test
    @DisplayName("CUSTOMER는 본인 주문을 결제할 수 있다")
    void createPayment_customerOwnOrder_success() {
        User customer = DomainTestFixture.customer();
        Order order = DomainTestFixture.order(CUSTOMER_ID, OrderStatus.ORDER_REQUESTED);
        PaymentCreateRequest request = DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(orderRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.existsByOrder_OrderId(ORDER_ID)).willReturn(false);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            DomainTestFixture.markPersisted(payment, PAYMENT_ID);
            return payment;
        });

        PaymentCreateResponse response = paymentService.createPayment(ORDER_ID, CUSTOMER_LOGIN_ID, request);

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getUserId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getAmount()).isEqualTo(order.getTotalPrice());
        assertThat(response.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("CUSTOMER가 아니면 결제를 생성할 수 없다")
    void createPayment_nonCustomer_fail() {
        User owner = DomainTestFixture.owner();
        PaymentCreateRequest request = DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD);

        given(userFinder.getUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);

        assertThatThrownBy(() -> paymentService.createPayment(ORDER_ID, OWNER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.FORBIDDEN_PAYMENT.getMessage());

        verifyNoInteractions(orderRepository, paymentRepository);
    }

    @Test
    @DisplayName("CUSTOMER는 타인 주문을 결제할 수 없다")
    void createPayment_otherCustomerOrder_fail() {
        User customer = DomainTestFixture.customer();
        Order otherCustomerOrder = DomainTestFixture.order(OTHER_CUSTOMER_ID, OrderStatus.ORDER_REQUESTED);
        PaymentCreateRequest request = DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(orderRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(otherCustomerOrder));

        assertThatThrownBy(() -> paymentService.createPayment(ORDER_ID, CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.FORBIDDEN_ORDER.getMessage());

        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("이미 결제 이력이 있는 주문은 다시 결제할 수 없다")
    void createPayment_duplicateOrder_fail() {
        User customer = DomainTestFixture.customer();
        Order order = DomainTestFixture.order(CUSTOMER_ID, OrderStatus.ORDER_REQUESTED);
        PaymentCreateRequest request = DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(orderRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.existsByOrder_OrderId(ORDER_ID)).willReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(ORDER_ID, CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.DUPLICATE_PAYMENT.getMessage());

        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("ORDER_REQUESTED 상태가 아니면 결제할 수 없다")
    void createPayment_notOrderRequested_fail() {
        User customer = DomainTestFixture.customer();
        Order acceptedOrder = DomainTestFixture.order(CUSTOMER_ID, OrderStatus.ORDER_ACCEPTED);
        PaymentCreateRequest request = DomainTestFixture.paymentCreateRequest(PaymentMethod.CARD);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(orderRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(acceptedOrder));

        assertThatThrownBy(() -> paymentService.createPayment(ORDER_ID, CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.INVALID_ORDER_STATUS_TRANSITION.getMessage());
    }

    @Test
    @DisplayName("CUSTOMER는 본인 결제를 상세 조회할 수 있다")
    void getPayment_customerOwnPayment_success() {
        User customer = DomainTestFixture.customer();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.SUCCESS);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(PAYMENT_ID, CUSTOMER_LOGIN_ID);

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("CUSTOMER는 타인 결제를 상세 조회할 수 없다")
    void getPayment_otherCustomerPayment_fail() {
        User customer = DomainTestFixture.customer();
        Payment otherCustomerPayment = DomainTestFixture.payment(OTHER_CUSTOMER_ID, PaymentStatus.SUCCESS);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(otherCustomerPayment));

        assertThatThrownBy(() -> paymentService.getPayment(PAYMENT_ID, CUSTOMER_LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.FORBIDDEN_PAYMENT.getMessage());
    }

    @Test
    @DisplayName("MASTER는 결제 상세를 조회할 수 있다")
    void getPayment_master_success() {
        User master = DomainTestFixture.master();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.SUCCESS);

        given(userFinder.getUserByLoginId(MASTER_LOGIN_ID)).willReturn(master);
        given(paymentRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(PAYMENT_ID, MASTER_LOGIN_ID);

        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("결제 검색은 CUSTOMER 본인 결제만 조회한다")
    void searchPayments_customer_filtersByUserId() {
        User customer = DomainTestFixture.customer();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.SUCCESS);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.searchPayments(eq(PaymentStatus.SUCCESS), eq(CUSTOMER_ID), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(payment)));

        PageResponse<PaymentSearchResponse> response =
                paymentService.searchPayments(PaymentStatus.SUCCESS, 0, 10, "desc", CUSTOMER_LOGIN_ID);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPaymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    @DisplayName("결제 검색은 MASTER에게 전체 결제 조회를 허용한다")
    void searchPayments_master_allPayments() {
        User master = DomainTestFixture.master();

        given(userFinder.getUserByLoginId(MASTER_LOGIN_ID)).willReturn(master);
        given(paymentRepository.searchPayments(isNull(), isNull(), eq(true), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        PageResponse<PaymentSearchResponse> response =
                paymentService.searchPayments(null, 0, 10, "desc", MASTER_LOGIN_ID);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("결제 검색은 page 음수와 허용되지 않는 size를 보정한다")
    void searchPayments_pageAndSize_normalized() {
        User customer = DomainTestFixture.customer();

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.searchPayments(isNull(), eq(CUSTOMER_ID), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        PageResponse<PaymentSearchResponse> response =
                paymentService.searchPayments(null, -1, 20, "desc", CUSTOMER_LOGIN_ID);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(paymentRepository).should()
                .searchPayments(isNull(), eq(CUSTOMER_ID), eq(false), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(response.getPage()).isZero();
    }

    @Test
    @DisplayName("CUSTOMER는 본인 결제를 취소할 수 있다")
    void cancelPayment_success() {
        // given: CUSTOMER 본인의 성공 결제와 취소 가능한 주문이 준비되어 있다.
        User customer = DomainTestFixture.customer();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.SUCCESS);
        PaymentCancelRequest request = DomainTestFixture.paymentCancelRequest("고객 요청으로 인한 주문 취소");
        Order order = DomainTestFixture.order(CUSTOMER_ID, OrderStatus.ORDER_REQUESTED);

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        // PaymentService는 주문 취소와 재고 복구를 OrderService에 위임한다.
        // OrderService는 mock이므로, 주문 상태 변경 효과만 테스트 안에서 재현한다.
        willAnswer(invocation -> {
            order.cancel();
            return null;
        }).given(orderService).cancelByPayment(ORDER_ID, CUSTOMER_LOGIN_ID);

        // when: CUSTOMER가 결제 취소를 요청한다.
        PaymentCancelResponse response = paymentService.cancelPayment(PAYMENT_ID, CUSTOMER_LOGIN_ID, request);

        // then: 결제는 CANCEL 상태가 되고, 주문 취소 처리는 OrderService로 위임된다.
        assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.CANCEL);
        assertThat(response.getCancelReason()).isEqualTo("고객 요청으로 인한 주문 취소");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCEL);
        assertThat(payment.getCanceledAt()).isNotNull();
        assertThat(payment.getCancelReason()).isEqualTo("고객 요청으로 인한 주문 취소");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        then(orderService).should().cancelByPayment(ORDER_ID, CUSTOMER_LOGIN_ID);
        verify(entityManager).flush();
    }

    @Test
    @DisplayName("SUCCESS가 아닌 결제는 취소할 수 없다")
    void cancelPayment_notSuccess_fail() {
        User customer = DomainTestFixture.customer();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.CANCEL);
        Order order = DomainTestFixture.order(CUSTOMER_ID, OrderStatus.ORDER_REQUESTED);
        PaymentCancelRequest request = DomainTestFixture.paymentCancelRequest("다시 취소 요청");

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.cancelPayment(PAYMENT_ID, CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.PAYMENT_CANCEL_NOT_ALLOWED.getMessage());

        verify(entityManager, never()).flush();
    }

    @Test
    @DisplayName("주문 상태가 ORDER_REQUESTED가 아니면 결제를 취소할 수 없다")
    void cancelPayment_notCancelableOrderStatus_fail() {
        User customer = DomainTestFixture.customer();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.SUCCESS);
        Order order = DomainTestFixture.order(CUSTOMER_ID, OrderStatus.ORDER_ACCEPTED);
        PaymentCancelRequest request = DomainTestFixture.paymentCancelRequest("고객 요청으로 인한 주문 취소");

        given(userFinder.getUserByLoginId(CUSTOMER_LOGIN_ID)).willReturn(customer);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(payment));
        given(orderRepository.findByOrderIdAndIsDeletedFalse(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.cancelPayment(PAYMENT_ID, CUSTOMER_LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.PAYMENT_CANCEL_NOT_ALLOWED.getMessage());

        verify(entityManager, never()).flush();
    }

    @Test
    @DisplayName("MASTER는 CANCEL 상태 결제를 soft delete 할 수 있다")
    void deletePayment_masterCancel_success() {
        User master = DomainTestFixture.master();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.CANCEL);

        given(userFinder.getMasterUserByLoginId(MASTER_LOGIN_ID)).willReturn(master);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(payment));

        paymentService.deletePayment(PAYMENT_ID, MASTER_LOGIN_ID);

        assertThat(payment.isDeleted()).isTrue();
        assertThat(payment.getDeletedBy()).isEqualTo(MASTER_LOGIN_ID);
        assertThat(payment.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("CANCEL 상태가 아닌 결제는 삭제할 수 없다")
    void deletePayment_notCancel_fail() {
        User master = DomainTestFixture.master();
        Payment payment = DomainTestFixture.payment(CUSTOMER_ID, PaymentStatus.SUCCESS);

        given(userFinder.getMasterUserByLoginId(MASTER_LOGIN_ID)).willReturn(master);
        given(paymentRepository.findByPaymentIdAndIsDeletedFalse(PAYMENT_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.deletePayment(PAYMENT_ID, MASTER_LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.PAYMENT_DELETE_NOT_ALLOWED.getMessage());

        assertThat(payment.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("MASTER가 아니면 결제를 삭제할 수 없다")
    void deletePayment_nonMaster_fail() {
        given(userFinder.getMasterUserByLoginId(CUSTOMER_LOGIN_ID))
                .willThrow(new UserDomainException(UserErrorCode.NOT_AUTHORIZATION));

        assertThatThrownBy(() -> paymentService.deletePayment(PAYMENT_ID, CUSTOMER_LOGIN_ID))
                .isInstanceOf(UserDomainException.class)
                .hasMessage(UserErrorCode.NOT_AUTHORIZATION.getMessage());

        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("OWNER는 결제 목록을 검색할 수 없다")
    void searchPayments_owner_fail() {
        User owner = DomainTestFixture.owner();

        given(userFinder.getUserByLoginId(OWNER_LOGIN_ID)).willReturn(owner);

        assertThatThrownBy(() -> paymentService.searchPayments(null, 0, 10, "desc", OWNER_LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GlobalErrorCode.FORBIDDEN_PAYMENT.getMessage());

        verifyNoInteractions(paymentRepository);
    }
}