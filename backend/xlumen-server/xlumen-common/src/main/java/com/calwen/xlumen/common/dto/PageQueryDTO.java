package com.calwen.xlumen.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分页查询参数基类（BACKEND.md §5.1）：列表分页请求 DTO 统一继承，
 * 子类用 {@code @SuperBuilder} 以让 builder 覆盖继承字段；
 * 字段默认值即接口默认值，pageSize 上限 100 由服务层统一截断。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PageQueryDTO {

    /** 页码（从 1 开始，默认 1）。 */
    @Builder.Default
    private long pageNo = 1;

    /** 每页条数（默认 20，上限 100）。 */
    @Builder.Default
    private long pageSize = 20;
}
