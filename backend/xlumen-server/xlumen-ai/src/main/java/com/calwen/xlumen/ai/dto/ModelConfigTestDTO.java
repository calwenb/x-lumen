package com.calwen.xlumen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型连通性测试入参（F-0502）：指定供应商+模型发一句 ping 验证连通性。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigTestDTO {

    /** 供应商（BAILIAN/DEEPSEEK/MOCK）。 */
    @NotBlank(message = "供应商不能为空")
    private String provider;

    /** 模型名。 */
    @NotBlank(message = "模型名不能为空")
    private String model;
}
