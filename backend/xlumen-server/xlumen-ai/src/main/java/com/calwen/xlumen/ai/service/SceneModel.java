package com.calwen.xlumen.ai.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景模型解析结果（F-0502）：供应商名 + 模型名（+ 场景参数）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneModel {

    /** 供应商名（BAILIAN/DEEPSEEK/MOCK）。 */
    private String providerName;

    /** 模型名。 */
    private String model;

    /** 场景参数（JSON 文本，可空）。 */
    private String paramsJson;
}
