package com.calwen.xlumen.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台操作审计日志实体（plt_activity_log，F-1202）：只增不改（append-only），仅 INSERT 不 UPDATE/DELETE。
 * operator_id 可空（系统/定时任务触发）；detail_json 为操作详情快照。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("plt_activity_log")
public class ActivityLogEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 操作人用户 ID（逻辑外键 iam_user.id，可空=系统触发）。 */
    private Long operatorId;

    /** 操作人名称（冗余展示字段）。 */
    private String operatorName;

    /** 操作类型（如 REVIEW_REJECT/KNOWLEDGE_PUBLISH/WORKSPACE_SETTINGS_UPDATE）。 */
    private String action;

    /** 目标类型（如 REVIEW/KNOWLEDGE/WORKSPACE）。 */
    private String targetType;

    /** 目标 ID。 */
    private Long targetId;

    /** 操作详情（JSON 文本，可空）。 */
    private String detailJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
