package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开文章查询参数（F-0201/F-0202）：关键词/分类/标签组合筛选 + 服务端分页（B01/B03）。
 * Spring MVC 自动绑定 GET 查询参数，字段默认值即接口默认值。
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

    /** 页码（从 1 开始，默认 1）。 */
    @Builder.Default
    private long pageNo = 1;

    /** 每页条数（默认 10，上限 100）。 */
    @Builder.Default
    private long pageSize = 10;
}
