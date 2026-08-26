package com.calwen.xlumen.content.api.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 公开知识查询参数：跨模块稳定入参（BACKEND.md §5.2），
 * publishing 公开读编排构造后传入，分页参数继承 {@link PageQueryDTO}。
 * KB-3 起分类筛选废弃，改为 kbId/directoryId 库级筛选（决策 D16）。
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

    /** 标签（JSON_CONTAINS 精确匹配，可空）。 */
    private String tag;

    /** 目标知识库 ID（筛选，可空）。 */
    private Long kbId;

    /** 目标目录 ID（筛选，0=库根，可空）。 */
    private Long directoryId;

    /** 可见库集合，由 publishing 按身份推导传入；为空=无可见库返回空页）。 */
    private List<Long> visibleKbIds;
}
