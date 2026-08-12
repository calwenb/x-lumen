package com.calwen.xlumen.publishing.dto;

import java.time.LocalDateTime;

/**
 * 评论视图（F-0203）：顶级评论与回复统一平铺返回，parentId 关联。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record CommentVO(Long id, Long articleId, Long parentId, String userName, String content, LocalDateTime createdAt) {
}
