package com.calwen.xlumen.publishing.dto;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 公开文章查询参数（F-0201/F-0202）：关键词/分类/标签组合筛选 + 服务端分页（B01/B03），
 * 分页参数继承 {@link PageQueryDTO}。Spring MVC 自动绑定 GET 查询参数，字段默认值即接口默认值。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArticleQueryDTO extends PageQueryDTO {

    /** 关键词（标题/摘要 LIKE，可空）。 */
    private String keyword;

    /** 分类（精确匹配，可空）。 */
    private String category;

    /** 标签（JSON_CONTAINS 精确匹配，可空）。 */
    private String tag;
}
