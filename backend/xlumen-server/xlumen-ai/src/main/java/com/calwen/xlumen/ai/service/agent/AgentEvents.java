package com.calwen.xlumen.ai.service.agent;

/**
 * Agent 运行事件回调（IDEA-025 F-0708）：编排层把过程事件转发给调用方
 * （对话→SSE chunk/tool 事件；后台任务→TaskContext 通道）。错误由调用方统一捕获走 error 事件。
 *
 * @author calwen
 * @date 2026/8/24
 */
public interface AgentEvents {

    /** 内容增量（流式逐块；非流式场景为整段一次性）。 */
    void onContent(String delta);

    /** 工具 start/done 事件。 */
    void onTool(ToolEvent event);

    /** 模型调用错误。 */
    void onError(Throwable error);
}