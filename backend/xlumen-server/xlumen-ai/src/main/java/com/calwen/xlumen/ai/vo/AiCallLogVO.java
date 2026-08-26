package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用追踪行视图（管理面展示）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCallLogVO {

    private Long id;

    private String scene;

    private String requestType;

    private String provider;

    private String model;

    private String promptHash;

    private Integer tokensIn;

    private Integer tokensOut;

    private BigDecimal estCost;

    private Boolean success;

    private Boolean degraded;

    private Integer latencyMs;

    private String errorMsg;

    private LocalDateTime createdAt;
}