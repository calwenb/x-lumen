package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景模型配置视图（F-0502）：列表展示，永不返回密钥。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigVO {

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 供应商（BAILIAN/DEEPSEEK/MOCK）。 */
    private String provider;

    /** 模型名。 */
    private String model;

    /** 场景参数（JSON 文本，可空）。 */
    private String paramsJson;
}
