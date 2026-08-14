package com.calwen.xlumen.content.api.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 公开知识查询参数（F-0201/F-0202）：跨模块稳定入参（BACKEND.md §5.2），
 * publishing 公开读编排构造后传入，分页参数继承 {@link PageQueryDTO}。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KnowledgeQueryDTO extends PageQueryDTO {

    /** 关键词（标题/摘要 LIKE，可空）。 */
    private String keyword;

    /** 分类（精确匹配，可空）。 */
    private String category;

    /** 标签（JSON_CONTAINS 精确匹配，可空）。 */
    private String tag;
}
