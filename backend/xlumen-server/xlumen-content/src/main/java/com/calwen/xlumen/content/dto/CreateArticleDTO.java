package com.calwen.xlumen.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建文章入参（F-0301）：新建即草稿状态，作者与空间取自登录态（WorkspaceContext）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateArticleDTO {

    /** 标题。 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    /** 正文 Markdown。 */
    private String content;

    /** 分类（可空）。 */
    @Size(max = 64, message = "分类不能超过 64 字")
    private String category;

    /** 标签数组（可空）。 */
    private List<String> tags;

    /** 可见性：1 公开 0 私有（F-0307，默认公开）。 */
    private Integer visibility;
}
