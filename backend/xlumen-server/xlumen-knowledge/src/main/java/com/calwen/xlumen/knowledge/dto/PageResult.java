package com.calwen.xlumen.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识模块通用分页结果（F-0305/F-0308）：统一 pageNo/pageSize 分页（BACKEND.md §10）。
 * 与 publishing 的 PageResult / identity 的 PageVO 语义一致，各模块自持避免跨模块依赖。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数。 */
    private long total;

    /** 当前页码（从 1 开始）。 */
    private long pageNo;

    /** 每页条数。 */
    private long pageSize;

    /** 当前页数据。 */
    private List<T> records;
}
