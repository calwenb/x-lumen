package com.calwen.xlumen.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站内消息实体（noti_notification，IDEA-024 通用通知模块）：只增不改，读状态 read_flag 翻转。
 * type 为业务事件类型（REVIEW=AI 审核结果；评论回复/@小光 F-1005 等后续事件复用）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Getter
@Setter
@TableName("noti_notification")
public class NotificationEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 接收用户 ID。 */
    private Long userId;

    /** 类型（REVIEW=AI 审核结果等）。 */
    private String type;

    /** 标题。 */
    private String title;

    /** 摘要内容。 */
    private String content;

    /** 跳转链接（前端路由，如 /studio/review）。 */
    private String link;

    /** 是否已读（0 未读 1 已读）。 */
    private Integer readFlag;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}