package com.calwen.xlumen.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作者文章列表查询参数（F-0301，B10 列表）：状态/可见性筛选 + 关键词 + 分页。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleListQueryDTO {

    /** 状态筛选（ArticleStatus 值，可空 = 全部）。 */
    private Integer status;

    /** 可见性筛选（1 公开 0 私有，可空 = 全部）。 */
    private Integer visibility;

    /** 标题关键词（LIKE，可空）。 */
    private String keyword;

    /** 页码（从 1 开始）。 */
    private long pageNo;

    /** 每页条数（≤100）。 */
    private long pageSize;
}
