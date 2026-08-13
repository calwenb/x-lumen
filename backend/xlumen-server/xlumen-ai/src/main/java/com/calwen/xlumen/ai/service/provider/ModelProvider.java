package com.calwen.xlumen.ai.service.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * 模型供应商抽象（F-0501）：统一 OpenAI 兼容对话与向量化接口。
 * available() 为 false（缺密钥）时由网关回退 MockProvider。
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
     * 非流式对话，返回完整回复。
     *
     * @param request 对话请求
     * @return 回复文本
     */
    String chat(ProviderChatRequest request);

    /**
     * 流式对话：逐块回调 onChunk，异常回调 onError。
     *
     * @param request 对话请求
     * @param onChunk 增量回调
     * @param onError 异常回调
     */
    void chatStream(ProviderChatRequest request, Consumer<String> onChunk, Consumer<Throwable> onError);

    /**
     * 文本向量化。
     *
     * @param text 待向量化文本
     * @return 向量
     */
    List<Float> embed(String text);
}
