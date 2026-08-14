package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分类/标签聚合项（F-0202）：名称 + 公开知识数。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryCountDTO {

    /** 分类/标签名称。 */
    private String name;

    /** 公开文章数。 */
    private long count;
}
