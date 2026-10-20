package com.georgia.jeogiyo.payment.controller;

import com.georgia.jeogiyo.global.response.CommonResponse;
import com.georgia.jeogiyo.global.response.PageResponse;
import com.georgia.jeogiyo.payment.dto.request.PaymentCancelRequest;
import com.georgia.jeogiyo.payment.dto.request.PaymentCreateRequest;
import com.georgia.jeogiyo.payment.dto.response.PaymentCancelResponse;
import com.georgia.jeogiyo.payment.dto.response.PaymentCreateResponse;
import com.georgia.jeogiyo.payment.dto.response.PaymentResponse;
import com.georgia.jeogiyo.payment.dto.response.PaymentSearchResponse;
import com.georgia.jeogiyo.payment.entity.PaymentStatus;
import com.georgia.jeogiyo.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payment", description = "결제 API - 현재는 외부 PG 연동 없이 즉시 승인 처리하는 가상 결제 방식입니다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 생성",
            description = "CUSTOMER가 본인 주문에 대한 결제를 생성합니다. 현재는 외부 PG 연동 없이 요청 즉시 PaymentStatus.SUCCESS로 저장하는 가상 결제이며, 결제 성공 후에도 주문 상태는 ORDER_REQUESTED를 유지합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "결제 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청값 검증 실패 또는 지원하지 않는 결제 수단"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "주문 없음"),
            @ApiResponse(responseCode = "409", description = "이미 결제가 존재하는 주문")
    })
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<CommonResponse<PaymentCreateResponse>> createPayment(
            @Parameter(description = "주문 ID", example = "66666666-6666-6666-6666-666666666661")
            @PathVariable UUID orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        String loginId = userDetails.getUsername();
        PaymentCreateResponse response = paymentService.createPayment(orderId, loginId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success("결제 생성 성공", response));
    }

    @Operation(summary = "결제 상세 조회", description = "CUSTOMER는 본인 결제, MASTER는 전체 결제를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 상세 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아니거나 권한 없음"),
            @ApiResponse(responseCode = "404", description = "결제 정보 없음")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_MASTER')")
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<CommonResponse<PaymentResponse>> getPayment(
            @Parameter(description = "결제 ID", example = "77777777-7777-7777-7777-777777777771")
            @PathVariable UUID paymentId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        String loginId = userDetails.getUsername();
        PaymentResponse response = paymentService.getPayment(paymentId, loginId);
        return ResponseEntity.ok(CommonResponse.success("결제 상세 조회 성공", response));
    }

    @Operation(summary = "결제 목록 검색", description = "CUSTOMER는 본인 결제, MASTER는 전체 결제를 검색합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 목록 검색 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_MASTER')")
    @GetMapping("/payments")
    public ResponseEntity<CommonResponse<PageResponse<PaymentSearchResponse>>> searchPayments(
            @Parameter(description = "결제 상태", example = "SUCCESS")
            @RequestParam(name = "paymentStatus", required = false) PaymentStatus paymentStatus,
            @Parameter(description = "페이지 번호. 음수 요청 시 0으로 보정됩니다.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 10, 30, 50만 허용하며 그 외 값은 10으로 보정됩니다.", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 방향. asc 또는 desc", example = "desc")
            @RequestParam(defaultValue = "desc") String sort,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        String loginId = userDetails.getUsername();
        PageResponse<PaymentSearchResponse> response =
                paymentService.searchPayments(paymentStatus, page, size, sort, loginId);

        return ResponseEntity.ok(CommonResponse.success("결제 목록 조회 성공", response));
    }

    @Operation(
            summary = "결제 취소",
            description = "CUSTOMER는 본인 결제, MASTER는 전체 결제를 취소할 수 있습니다. 결제 취소 시 주문 취소와 재고 복구가 함께 처리됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 취소 성공"),
            @ApiResponse(responseCode = "400", description = "취소 불가능한 결제 상태 또는 주문 상태"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아니거나 권한 없음"),
            @ApiResponse(responseCode = "404", description = "결제 또는 주문 정보 없음")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_MASTER')")
    @PatchMapping("/payments/{paymentId}/cancel")
    public ResponseEntity<CommonResponse<PaymentCancelResponse>> cancelPayment(
            @Parameter(description = "결제 ID", example = "77777777-7777-7777-7777-777777777771")
            @PathVariable UUID paymentId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PaymentCancelRequest request
    ) {
        String loginId = userDetails.getUsername();
        PaymentCancelResponse response = paymentService.cancelPayment(paymentId, loginId, request);
        return ResponseEntity.ok(CommonResponse.success("결제 취소 성공", response));
    }

    @Operation(summary = "결제 이력 삭제", description = "MASTER가 취소 상태 결제를 soft delete 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결제 이력 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "취소 상태가 아닌 결제는 삭제 불가"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "MASTER 권한 없음"),
            @ApiResponse(responseCode = "404", description = "결제 정보 없음")
    })
    @PreAuthorize("hasAuthority('ROLE_MASTER')")
    @DeleteMapping("/payments/{paymentId}")
    public ResponseEntity<CommonResponse<Void>> deletePayment(
            @Parameter(description = "결제 ID", example = "77777777-7777-7777-7777-777777777771")
            @PathVariable UUID paymentId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        String loginId = userDetails.getUsername();
        paymentService.deletePayment(paymentId, loginId);
        return ResponseEntity.ok(CommonResponse.<Void>success("결제 이력 삭제 성공", null));
    }
}