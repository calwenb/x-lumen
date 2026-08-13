package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章发布/状态迁移入参（跨模块稳定类型，M10 发布与下架）：publishing 通过 ContentApi 迁移状态，
 * 版本校验失败返回 false（由调用方抛 409）。publishing 不直接操作 cnt_article（BACKEND.md §5.1）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePublishDTO {

    /** 文章 ID。 */
    private Long articleId;

    /** 期望版本号（乐观锁，不一致返回 false）。 */
    private Long expectedVersion;

    /** 目标状态（ArticleStatus 值）。 */
    private Integer targetStatus;

    /** 可见性：1 公开 0 私有（发布时生效，F-0307）。 */
    private Integer visibility;

    /** 发布时间（发布成功写入，其余迁移为 null）。 */
    private LocalDateTime publishedAt;
}
