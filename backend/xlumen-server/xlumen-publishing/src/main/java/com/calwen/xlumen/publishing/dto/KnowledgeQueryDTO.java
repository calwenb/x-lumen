package com.calwen.xlumen.publishing.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 公开知识查询参数（F-0201/F-0202）：关键词/标签/库级筛选组合 + 服务端分页（B01/B03），
 * 分页参数继承 {@link PageQueryDTO}。Spring MVC 自动绑定 GET 查询参数，字段默认值即接口默认值。
 * KB-3 起 category 废弃（决策 D16 改目录树），改 kbId/directoryId 库级筛选。
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

    /** 目标知识库 ID（筛选，可空）。 */
    private Long kbId;

    /** 目标目录 ID（筛选，0=库根，可空）。 */
    private Long directoryId;

    /** 标签（JSON_CONTAINS 精确匹配，可空）。 */
    private String tag;
}
