package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 审校问题视图：严重度/原文位置/证据/修改建议。
 * 事实核对模式：可选字段 evidenceKnowledgeId/evidenceQuote 携带库内证据引用
 * （旧结果无证据字段依旧合法，Schema 兼容）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssueVO {

    /** 严重度：error|warning|info。 */
    private String severity;

    /** 原文位置引用。 */
    private String position;

    /** 证据。 */
    private String evidence;

    /** 修改建议。 */
    private String suggestion;

    /** 库内证据知识 ID（事实核对模式可选）。 */
    private String evidenceKnowledgeId;

    /** 库内证据原文引用（事实核对模式可选）。 */
    private String evidenceQuote;
}
