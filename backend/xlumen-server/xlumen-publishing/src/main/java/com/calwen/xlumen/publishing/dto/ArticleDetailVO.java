package com.calwen.xlumen.publishing.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情（B02，F-0201）：正文 + 互动统计 + 当前用户点赞状态（登录时）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record ArticleDetailVO(
        Long id,
        String title,
        String summary,
        String content,
        String authorName,
        String category,
        List<String> tags,
        Long viewCount,
        int readMinutes,
        long commentCount,
        long likeCount,
        boolean liked,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
