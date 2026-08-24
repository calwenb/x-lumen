package com.calwen.xlumen.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 任务完结事件（进程内，IDEA-024）：AiTaskServiceImpl.complete/fail 发布，
 * 供通知模块等跨模块监听消费（任务在异步线程执行，无 WorkspaceContext，身份以显式字段为准）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskCompletedEvent {

    /** 任务 ID。 */
    private Long taskId;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 发起用户 ID（通知接收人）。 */
    private Long userId;

    /** 场景（AiScene 名）。 */
    private String scene;

    /** 状态：COMPLETED|FAILED。 */
    private String status;

    /** 任务结果（JSON 文本，可空）。 */
    private String resultJson;

    /** 失败原因（对外脱敏，可空）。 */
    private String errorMsg;

    /** 任务入参快照（JSON 文本，可空）。 */
    private String inputJson;
}