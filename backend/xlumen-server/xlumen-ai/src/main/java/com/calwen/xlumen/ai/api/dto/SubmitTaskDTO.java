package com.calwen.xlumen.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 任务提交入参（跨模块稳定类型，M07/M10）：调用方（content/publishing）通过 AiApi 提交异步任务，
 * AI 模块不反向依赖调用方（BACKEND.md §14）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitTaskDTO {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 发起用户 ID。 */
    private Long userId;

    /** 场景（AiScene 名：WRITING/REVIEWER）。 */
    private String scene;

    /** 任务入参（JSON 文本）。 */
    private String inputJson;

    /** 业务幂等键（重复提交返回已有任务，F-0905 精神）。 */
    private String idempotencyKey;
}
