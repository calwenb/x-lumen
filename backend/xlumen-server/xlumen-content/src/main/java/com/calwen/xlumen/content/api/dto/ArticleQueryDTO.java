package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开文章查询参数（F-0201/F-0202）：跨模块稳定入参（BACKEND.md §5.2），publishing 公开读编排构造后传入。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleQueryDTO {

    /** 关键词（标题/摘要 LIKE，可空）。 */
    private String keyword;

    /** 分类（精确匹配，可空）。 */
    private String category;

    /** 标签（JSON_CONTAINS 精确匹配，可空）。 */
    private String tag;

    /** 页码（从 1 开始）。 */
    private long pageNo;

    /** 每页条数（≤100）。 */
    private long pageSize;
}
