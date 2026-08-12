package com.calwen.xlumen.content.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开文章卡片（F-0201）：公开列表展示用，不返回正文（详情接口才返回）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record PublishedArticleDTO(
        Long id,
        String title,
        String summary,
        String authorName,
        String category,
        List<String> tags,
        Long viewCount,
        int readMinutes,
        LocalDateTime publishedAt) {
}
