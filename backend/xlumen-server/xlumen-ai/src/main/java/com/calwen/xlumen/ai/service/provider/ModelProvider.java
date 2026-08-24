package com.calwen.xlumen.ai.service.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * 模型供应商抽象（F-0501）：统一 OpenAI 兼容对话与向量化接口。
 * available() 为 false（缺密钥）时由网关回退 MockProvider。
 * 对话契约（IDEA-025 F-0708）：chat() 返回 ProviderChatResult（含 tool_calls/finish_reason），
 * chatStream() 以 StreamCallback 回调内容增量与流终态。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ModelProvider {

    /**
     * 供应商名称（大写，如 BAILIAN/DEEPSEEK/MOCK）。
     *
     * @return 供应商名
     */
    String name();

    /**
     * 是否可用（已配置密钥）。
     *
     * @return 可用返回 true
     */
    boolean available();

    /**
     * 非流式对话，返回完整回复与工具调用。
     *
     * @param request 对话请求
     * @return 统一结果（内容 + 工具调用 + 结束原因）
     */
    ProviderChatResult chat(ProviderChatRequest request);

    /**
     * 流式对话：逐块回调内容增量，流结束回调终态，异常回调 onError。
     *
     * @param request  对话请求
     * @param callback 增量与终态回调
     * @param onError  异常回调
     */
    void chatStream(ProviderChatRequest request, StreamCallback callback, Consumer<Throwable> onError);

    /**
     * 文本向量化。
     *
     * @param text 待向量化文本
     * @return 向量
     */
    List<Float> embed(String text);
}