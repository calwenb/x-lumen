package com.calwen.xlumen.content.api.dto;

import java.util.List;

/**
 * 分页结果（Api 稳定类型，BACKEND.md §5.2：不返回分页插件内部对象）。
 *
 * @param total    总记录数
 * @param pageNo   当前页码
 * @param pageSize 每页条数
 * @param records  当前页数据
 * @author calwen
 * @date 2026/8/12
 */
public record ContentPageResult<T>(long total, long pageNo, long pageSize, List<T> records) {
}
