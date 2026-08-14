package com.calwen.xlumen.knowledge.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新知识库入参（F-0308）：名称/简介/封面可改；可见性切换走独立接口（{id}/visibility，审计+缓存失效）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKnowledgeBaseDTO {

    /** 名称（空间内唯一，1~64 字，可空=不修改）。 */
    @Size(max = 64, message = "知识库名称不能超过 64 字")
    private String name;

    /** 简介（≤500 字，可空=不修改）。 */
    @Size(max = 500, message = "简介不能超过 500 字")
    private String intro;

    /** 封面 URL（≤255 字，可空=不修改）。 */
    @Size(max = 255, message = "封面 URL 不能超过 255 字")
    private String cover;
}
