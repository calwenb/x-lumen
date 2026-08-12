package com.calwen.xlumen.publishing.engagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文章评论实体（eng_comment，F-0203）：parent_id 支持回复。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName("eng_comment")
public class CommentEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（逻辑外键 cnt_article.id）。 */
    private Long articleId;

    /** 评论用户 ID（逻辑外键 iam_user.id）。 */
    private Long userId;

    /** 评论用户名（冗余展示字段）。 */
    private String userName;

    /** 回复的评论 ID（NULL 为顶级评论）。 */
    private Long parentId;

    /** 评论内容。 */
    private String content;

    /** 状态：1 正常 0 删除。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
