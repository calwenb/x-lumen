package com.calwen.xlumen.content.api.dto;

/**
 * 分类/标签聚合项（F-0202）：名称 + 公开文章数。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record CategoryCountDTO(String name, long count) {
}
