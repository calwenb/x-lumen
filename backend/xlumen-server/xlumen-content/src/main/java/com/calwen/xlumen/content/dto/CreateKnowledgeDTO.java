package com.calwen.xlumen.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建知识入参（F-0301，决策 D16）：新建即草稿状态，作者与空间取自登录态（WorkspaceContext）；
 * 必须归属一个知识库（kbId 必填，单库单目录）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeDTO {

    /** 标题。 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    /** 正文 Markdown。 */
    private String content;

    /** 所属知识库 ID（必填，单库单目录，决策 D16）。 */
    @NotNull(message = "请选择知识库")
    private Long kbId;

    /** 所属目录 ID（0=库根，可空默认 0）。 */
    private Long directoryId;

    /** 标签数组（可空）。 */
    private List<String> tags;
}
