package com.calwen.xlumen.publishing.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章卡片（B01 列表，F-0201/F-0202）：含互动统计（评论/点赞数由 engagement 域聚合，避免 N+1）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record ArticleCardVO(
        Long id,
        String title,
        String summary,
        String authorName,
        String category,
        List<String> tags,
        Long viewCount,
        int readMinutes,
        long commentCount,
        long likeCount,
        LocalDateTime publishedAt) {
}
