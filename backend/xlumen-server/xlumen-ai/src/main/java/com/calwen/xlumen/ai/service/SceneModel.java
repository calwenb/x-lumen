package com.calwen.xlumen.ai.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景模型解析结果：供应商名 + 模型名（+ 场景参数/提示词/配额）。
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

    /** 场景提示词（覆盖默认；WRITING 为 JSON 多槽位，空=回退常量默认）。 */
    private String prompt;

    /** 每日调用配额（0=不限）。 */
    private Integer dailyQuota;
}
