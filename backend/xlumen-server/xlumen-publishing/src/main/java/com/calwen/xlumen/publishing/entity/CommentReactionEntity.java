package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 评论反应实体（eng_comment_reaction，F-0213）：唯一键 (workspace_id, comment_id, user_id) 承担幂等，
 * 赞/踩共用一行三态互斥（语义与 eng_like 一致），取消/切换更新 status 与 reaction_type。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Getter
@Setter
@TableName("eng_comment_reaction")
public class CommentReactionEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 评论 ID（逻辑外键 eng_comment.id）。 */
    private Long commentId;

    /** 反应用户 ID（逻辑外键 iam_user.id）。 */
    private Long userId;

    /** 反应类型：1 点赞 2 点踩。 */
    private Integer reactionType;

    /** 状态：1 活动中 0 已取消。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
