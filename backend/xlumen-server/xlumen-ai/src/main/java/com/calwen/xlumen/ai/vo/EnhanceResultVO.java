package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 增值结果视图（F-0801/F-0802）：摘要/SEO 结构化结果。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhanceResultVO {

    /** 结果 ID。 */
    private Long id;

    /** 文章 ID（可空）。 */
    private Long articleId;

    /** 场景：SUMMARY|SEO。 */
    private String scene;

    /** 结构化结果（JSON 文本）。 */
    private String resultJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
