package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果（Api 稳定类型，BACKEND.md §5.2：不返回分页插件内部对象）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentPageResult<T> {

    /** 总记录数。 */
    private long total;

    /** 当前页码（从 1 开始）。 */
    private long pageNo;

    /** 每页条数。 */
    private long pageSize;

    /** 当前页数据。 */
    private List<T> records;
}
