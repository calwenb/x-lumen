package com.calwen.xlumen.publishing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 读者纠错视图（F-1001）：以 trackNo 作为对外追踪号。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackVO {

    /** 追踪号（雪花 ID 后 12 位大写字母数字）。 */
    private String trackNo;

    /** 纠错位置（可空）。 */
    private String position;

    /** 问题描述。 */
    private String problem;

    /** 证据/建议（可空）。 */
    private String evidence;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
