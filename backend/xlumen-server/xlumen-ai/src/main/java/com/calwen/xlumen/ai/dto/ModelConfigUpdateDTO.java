package com.calwen.xlumen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景模型配置更新入参（管理面 A03）：密钥不入表，仅改供应商/模型/参数/提示词/配额。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigUpdateDTO {

    /** 供应商（BAILIAN/DEEPSEEK/MOCK）。 */
    @NotBlank(message = "供应商不能为空")
    private String provider;

    /** 模型名。 */
    @NotBlank(message = "模型名不能为空")
    private String model;

    /** 场景参数（JSON 文本，可空）。 */
    private String paramsJson;

    /** 场景提示词（null=保持原值，空串=清除回默认；WRITING 为多槽位 JSON）。 */
    private String prompt;

    /** 每日调用配额（null=保持原值，0=不限）。 */
    private Integer dailyQuota;
}
