package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 读者纠错实体（eng_feedback，F-1001）：匿名可提交（user_id 可空）；
 * track_no 业务唯一（uk_feedback_track_no），对外作为追踪号。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("eng_feedback")
public class FeedbackEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（逻辑外键 cnt_article.id）。 */
    private Long articleId;

    /** 提交用户 ID（逻辑外键 iam_user.id，可空=匿名）。 */
    private Long userId;

    /** 纠错位置（可空）。 */
    private String position;

    /** 问题描述。 */
    private String problem;

    /** 证据/建议（可空）。 */
    private String evidence;

    /** 追踪号（雪花 ID 后 12 位大写字母数字）。 */
    private String trackNo;

    /** 状态：1 待处理 0 已处理。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
