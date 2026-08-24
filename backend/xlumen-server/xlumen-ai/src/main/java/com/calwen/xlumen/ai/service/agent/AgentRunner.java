package com.calwen.xlumen.ai.service.agent;

/**
 * Agent 编排器（IDEA-025 F-0708）：多轮工具循环（模型自主检索→执行工具→继续作答），
 * 同时支持流式（对话）与非流式（审校/SEO 轻量）两种模式。
 *
 * @author calwen
 * @date 2026/8/24
 */
public interface AgentRunner {

    /**
     * 运行 Agent 循环：在调用方线程内同步驱动（无异步复杂度）。
     *
     * @param request 运行请求（身份/会话/初始消息/场景）
     * @param events  过程事件回调
     * @return 运行结果
     */
    AgentResult run(AgentRequest request, AgentEvents events);
}