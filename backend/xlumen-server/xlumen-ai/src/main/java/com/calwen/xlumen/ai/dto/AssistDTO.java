package com.calwen.xlumen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 交互式辅助入参（F-0606 辅助编辑 / F-0607 代码解读）：
 * action 指定能力，content 为全文（文章或代码），selection/title 为可选上下文。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistDTO {

    /** 能力：continue 续写 / polish 润色 / titles 标题生成 / spellfix 错别字修正 /
     *  code_explain 代码解释 / code_bug 找 bug / code_test 生成测试 /
     *  kb_insight 库主题概览 / kb_cluster 库内容聚类（输入编号列表行）。 */
    @NotBlank(message = "能力不能为空")
    private String action;

    /** 全文内容（文章 Markdown 或代码片段）。 */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 选中文本（可选上下文，可空）。 */
    private String selection;

    /** 主题/原标题（可选上下文，可空）。 */
    private String title;
}