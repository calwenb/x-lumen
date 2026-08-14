package com.calwen.xlumen.content.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 作者知识列表查询参数（F-0301，B10 列表，决策 D16）：库/目录/状态筛选 + 关键词，分页参数继承 {@link PageQueryDTO}；
 * 排序固定更新时间倒序（PRODUCT §6 排序规则）。
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

    /** 知识库筛选（可空 = 全部）。 */
    private Long kbId;

    /** 目录筛选（0=库根，可空 = 全部）。 */
    private Long directoryId;

    /** 标题关键词（LIKE，可空）。 */
    private String keyword;
}
