package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 公开接口分页结果（F-0201/F-0202）：统一 pageNo/pageSize 分页（BACKEND.md §10/§18）。
 *
 * @author calwen
 * @date 2026/8/12
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
