package com.calwen.xlumen.content.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项（F-0301，B10 列表）：不含正文，含状态/可见性/版本便于筛选与编辑跳转。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleListItemVO {

    /** 文章 ID。 */
    private Long id;

    /** 标题。 */
    private String title;

    /** 分类。 */
    private String category;

    /** 标签数组。 */
    private List<String> tags;

    /** 可见性：1 公开 0 私有（F-0307）。 */
    private Integer visibility;

    /** 状态（ArticleStatus 值）。 */
    private Integer status;

    /** 版本号。 */
    private Long version;

    /** 阅读量。 */
    private Long viewCount;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
