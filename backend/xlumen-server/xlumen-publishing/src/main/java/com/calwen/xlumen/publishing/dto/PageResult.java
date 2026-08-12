package com.calwen.xlumen.publishing.dto;

import java.util.List;

/**
 * 公开接口分页结果（F-0201/F-0202）：统一 pageNo/pageSize 分页（BACKEND.md §10/§18）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record PageResult<T>(long total, long pageNo, long pageSize, List<T> records) {
}
