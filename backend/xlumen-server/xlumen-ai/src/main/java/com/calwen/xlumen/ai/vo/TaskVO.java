package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 任务视图（F-1302）：任务状态与结果对外展示，resultJson/errorMsg 为文本。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskVO {

    /** 任务 ID。 */
    private Long id;

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 状态（AiTaskStatus 名）。 */
    private String status;

    /** 任务结果（JSON 文本，COMPLETED 后非空）。 */
    private String resultJson;

    /** 失败原因（对外脱敏）。 */
    private String errorMsg;
}
