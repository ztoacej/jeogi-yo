package com.georgia.jeogiyo.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    // 공통
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "해당 요청에 대한 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 오류가 발생했습니다."),

    // 회원
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용중인 닉네임입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용중인 아이디입니다."),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    INVALID_LOGIN_INFO(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    DELETED_USER(HttpStatus.UNAUTHORIZED, "탈퇴한 회원입니다."),
    ALREADY_DELETED_USER(HttpStatus.CONFLICT, "이미 탈퇴한 회원입니다."),
    DELETE_FAILURE_LAST_MASTER(HttpStatus.CONFLICT, "현재 마지막 관리자이므로 회원탈퇴가 불가능합니다."),
    DELETE_FAILURE_OPEN_STORES(HttpStatus.CONFLICT, "먼저 가게 삭제/폐업 처리 후 MASTER에게 문의하세요."),

    // 배송지
    NOT_FOUND_ADDRESS(HttpStatus.NOT_FOUND, "존재하지 않는 배송지입니다."),
    FORBIDDEN_ADDRESS(HttpStatus.FORBIDDEN, "본인의 배송지가 아닙니다."),
    OUT_OF_SERVICE_AREA(HttpStatus.BAD_REQUEST, "서비스 가능 지역이 아닙니다."),
    ALREADY_DELETED_ADDRESS(HttpStatus.CONFLICT, "이미 삭제된 배송지입니다."),
    ALREADY_DELETED_LAST_ADDRESS(HttpStatus.CONFLICT, "기본 배송지 하나만 있는 경우 삭제 처리가 불가합니다."),

    // 카테고리
    NOT_FOUND_CATEGORY(HttpStatus.NOT_FOUND, "존재하지 않는 카테고리입니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "이미 존재하는 카테고리 이름입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "해당 카테고리를 사용하는 가게 또는 상품이 존재합니다."),
    ALREADY_DELETED_CATEGORY(HttpStatus.CONFLICT, "이미 삭제된 카테고리입니다."),

    // 가게
    NOT_FOUND_STORE(HttpStatus.NOT_FOUND, "존재하지 않는 가게입니다."),
    FORBIDDEN_STORE(HttpStatus.FORBIDDEN, "본인의 가게가 아닙니다."),
    INVALID_STORE_STATUS(HttpStatus.CONFLICT, "허용되지 않은 가게 상태 변경입니다."),
    STORE_HAS_ACTIVE_ORDERS(HttpStatus.CONFLICT, "진행 중인 주문이 있는 가게는 삭제할 수 없습니다."),
    ALREADY_DELETED_STORE(HttpStatus.CONFLICT, "이미 삭제된 가게입니다."),

    // 상품
    NOT_FOUND_PRODUCT(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    FORBIDDEN_PRODUCT(HttpStatus.FORBIDDEN, "본인 소유 가게의 상품이 아닙니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "상품 가격은 0보다 커야 합니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "상품 재고는 0 이상이어야 합니다."),
    ALREADY_DELETED_PRODUCT(HttpStatus.CONFLICT, "이미 삭제된 상품입니다."),
    HIDDEN_PRODUCT_NOT_READABLE(HttpStatus.FORBIDDEN, "숨김 처리된 상품은 조회할 수 없습니다."),

    // 주문
    NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    FORBIDDEN_ORDER(HttpStatus.FORBIDDEN, "해당 주문에 접근할 권한이 없습니다."),
    STORE_NOT_OPEN(HttpStatus.CONFLICT, "영업중인 가게가 아닙니다."),
    ORDER_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "주문 금액은 2,147,483,647원을 초과할 수 없습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "상품 재고가 부족합니다."),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 주문 상태 변경입니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "주문을 취소할 수 없는 상태입니다."),
    PRODUCT_NOT_IN_STORE(HttpStatus.BAD_REQUEST, "요청한 가게에 속하지 않은 상품입니다."),
    PRODUCT_NOT_ORDERABLE(HttpStatus.CONFLICT, "주문할 수 없는 상품입니다."),
    ORDER_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "이미 수락된 주문은 취소할 수 없습니다."),
    ORDER_CANCEL_TIME_EXPIRED(HttpStatus.CONFLICT, "주문 후 5분이 지나 취소할 수 없습니다."),
    NO_PAYMENT_FOUND_FOR_ORDER(HttpStatus.CONFLICT, "결제 이력이 없는 주문입니다."),
    PAYMENT_NOT_SUCCESS(HttpStatus.CONFLICT, "결제가 완료된 주문만 수락할 수 있습니다."),

    // 결제
    NOT_FOUND_PAYMENT(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "이미 결제가 존재하는 주문입니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "결제를 취소할 수 없는 상태입니다."),
    PAYMENT_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "취소된 결제만 삭제할 수 있습니다."),
    FORBIDDEN_PAYMENT(HttpStatus.FORBIDDEN,  "본인 결제 내역만 조회 가능합니다"),

    // 리뷰
    NOT_FOUND_REVIEW(HttpStatus.NOT_FOUND, "존재하지 않는 리뷰입니다."),
    FORBIDDEN_REVIEW(HttpStatus.FORBIDDEN, "작성자 본인만 가능합니다."),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "이미 해당 주문에 대한 리뷰가 존재합니다."),
    REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT, "배송 완료된 주문만 리뷰를 작성할 수 있습니다."),
    INVALID_REVIEW_RATING(HttpStatus.BAD_REQUEST, "평점은 1점부터 5점 사이여야 합니다."),
    EMPTY_REVIEW_UPDATE(HttpStatus.BAD_REQUEST, "수정할 평점 또는 내용을 입력해 주세요."),
    ALREADY_DELETED_REVIEW(HttpStatus.CONFLICT, "이미 삭제된 리뷰입니다."),

    // AI 이력
    NOT_FOUND_AI_HISTORY(HttpStatus.NOT_FOUND, "존재하지 않는 AI 응답 이력입니다."),
    AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 상품 설명 생성에 실패했습니다."),
    EMPTY_AI_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini 응답이 비어 있습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String message;

}
