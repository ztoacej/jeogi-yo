package com.georgia.jeogiyo.ai.dto.response;

import com.georgia.jeogiyo.ai.entity.AiStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;


/**
 * I 응답 이력 상세 조회 응답
 * */
@Getter
@Builder
@AllArgsConstructor
public class AiHistoryResponse {

    private UUID aiHistoryId;

    @Schema(description = "상품 ID. 상품 등록 중 AI 생성 실패로 상품이 없으면 null", nullable = true)
    private UUID productId;

    private UUID userId;

    private String requestText;

    private String responseText;

    private String modelName;

    private AiStatus aiStatus;

    private String errorMessage;

}