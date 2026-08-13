package com.calwen.xlumen.ai.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 任务结果（跨模块稳定类型）：调用方轮询获取任务状态与结构化结果（result_json 文本）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResultVO {

    /** 任务 ID。 */
    private Long taskId;

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 状态（AiTaskStatus 名）。 */
    private String status;

    /** 任务结果（JSON 文本，COMPLETED 后非空）。 */
    private String resultJson;

    /** 失败原因（对外脱敏）。 */
    private String errorMsg;
}
