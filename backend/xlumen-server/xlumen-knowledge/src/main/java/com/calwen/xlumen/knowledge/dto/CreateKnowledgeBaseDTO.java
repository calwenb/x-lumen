package com.calwen.xlumen.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建知识库入参（F-0308）：名称空间内唯一（uk_kb_ws_name）；可见性默认私有（0）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeBaseDTO {

    /** 名称（空间内唯一，1~64 字）。 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "知识库名称不能超过 64 字")
    private String name;

    /** 简介（可空，≤500 字）。 */
    @Size(max = 500, message = "简介不能超过 500 字")
    private String intro;

    /** 封面 URL（可空）。 */
    @Size(max = 255, message = "封面 URL 不能超过 255 字")
    private String cover;

    /** 可见性：0 私有 1 公开（默认私有，F-0308）。 */
    private Integer visibility;
}
