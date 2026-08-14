package com.calwen.xlumen.content.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 作者知识列表查询参数（F-0301，B10 列表）：状态/可见性筛选 + 关键词，分页参数继承 {@link PageQueryDTO}。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KnowledgeListQueryDTO extends PageQueryDTO {

    /** 状态筛选（KnowledgeStatus 值，可空 = 全部）。 */
    private Integer status;

    /** 可见性筛选（1 公开 0 私有，可空 = 全部）。 */
    private Integer visibility;

    /** 标题关键词（LIKE，可空）。 */
    private String keyword;
}
