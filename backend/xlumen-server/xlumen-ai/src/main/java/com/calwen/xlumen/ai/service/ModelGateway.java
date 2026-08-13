package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;

import java.util.List;
import java.util.function.Consumer;

/**
 * 模型网关（F-0501/F-0502）：场景→(供应商,模型) 解析 + 对话/向量化编排 + 简单熔断。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ModelGateway {

    /**
     * 解析场景对应的供应商与模型（表优先，回退 AiProperties 默认）。
     *
     * @param workspaceId 工作空间 ID（可空，空则直接用默认）
     * @param scene       场景
     * @return 场景模型
     */
    SceneModel resolveScene(Long workspaceId, AiScene scene);

    /**
     * 非流式对话。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param request     对话请求
     * @return 回复文本
     */
    String chat(Long workspaceId, AiScene scene, ProviderChatRequest request);

    /**
     * 流式对话。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param request     对话请求
     * @param onChunk     增量回调
     * @param onError     异常回调
     */
    void chatStream(Long workspaceId, AiScene scene, ProviderChatRequest request,
                    Consumer<String> onChunk, Consumer<Throwable> onError);

    /**
     * 文本向量化。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param text        待向量化文本
     * @return 向量
     */
    List<Float> embed(Long workspaceId, AiScene scene, String text);

    /**
     * 连通性测试：指定供应商+模型发一句 ping。
     *
     * @param providerName 供应商名
     * @param model        模型名
     * @return 连通返回 true
     */
    boolean test(String providerName, String model);
}
