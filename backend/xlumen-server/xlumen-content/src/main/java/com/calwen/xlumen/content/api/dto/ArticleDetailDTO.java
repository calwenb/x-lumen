package com.calwen.xlumen.content.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开文章详情（F-0201）：已发布公开文章正文快照（F-0307 过滤后）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record ArticleDetailDTO(
        Long id,
        String title,
        String summary,
        String content,
        String authorName,
        String category,
        List<String> tags,
        Long viewCount,
        int readMinutes,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
