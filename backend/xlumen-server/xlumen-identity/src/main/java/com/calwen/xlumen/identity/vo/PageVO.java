package com.calwen.xlumen.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 身份模块通用分页结果：统一 pageNo/pageSize 分页（与 publishing 的 PageResult 语义一致，避免跨模块依赖）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {

    /** 总记录数。 */
    private long total;

    /** 当前页码（从 1 开始）。 */
    private long pageNo;

    /** 每页条数。 */
    private long pageSize;

    /** 当前页数据。 */
    private List<T> records;
}
