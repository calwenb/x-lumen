package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论视图（F-0203）：顶级评论与回复统一平铺返回，parentId 关联。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    /** 评论 ID（雪花 ID，字符串传输，BACKEND.md §5.3）。 */
    private Long id;

    /** 知识 ID。 */
    private Long knowledgeId;

    /** 回复的评论 ID（NULL 为顶级评论）。 */
    private Long parentId;

    /** 评论用户名（冗余展示字段）。 */
    private String userName;

    /** 评论内容。 */
    private String content;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
