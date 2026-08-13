package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 审校问题视图（F-0604）：严重度/原文位置/证据/修改建议。
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
}
