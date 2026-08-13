package com.calwen.xlumen.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志视图（F-1202）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogVO {

    /** 日志 ID。 */
    private Long id;

    /** 操作人名称。 */
    private String operatorName;

    /** 操作类型。 */
    private String action;

    /** 目标类型。 */
    private String targetType;

    /** 目标 ID。 */
    private Long targetId;

    /** 操作详情（JSON 文本，可空）。 */
    private String detailJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
