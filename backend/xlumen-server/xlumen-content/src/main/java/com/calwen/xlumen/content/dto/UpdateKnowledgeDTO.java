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
 * 更新知识入参（F-0301）：携带版本号做乐观锁校验，冲突返回 409（PRODUCT §6 禁止静默覆盖）；
 * 仅构思/草稿状态可编辑，已发布版本不可修改（PRODUCT §4）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKnowledgeDTO {

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

    /** 可见性：1 公开 0 私有（F-0307）。 */
    private Integer visibility;

    /** 版本号（乐观锁，必须与服务端一致）。 */
    @NotNull(message = "版本号不能为空")
    private Long version;
}
