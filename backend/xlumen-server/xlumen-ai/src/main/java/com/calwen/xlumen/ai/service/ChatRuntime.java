package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.tool.ToolRun;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 运行时（OPT-2/D20 全量迁移，替代 ModelGateway/AgentRunner）：场景→(供应商,模型) 解析 +
 * Spring AI ChatModel/ChatClient 调用编排 + 简单熔断 + 无密钥回退 ScriptedChatModel。
 * 无工具调用用 chat/chatStream（ChatModel 直调）；工具化路径用 chatWithTools/chatStreamWithTools
 * （ChatClient + ToolCallingAdvisor 自动多轮工具循环，工具执行中间态经 ToolRun.sink 收集）。
 * 模型名按场景表解析并逐次经 options 下发；供应商 API Key 缺失时回退脚本模型（离线可测）。
 *
 * @author calwen
 * @date 2026/8/24
 */
public interface ChatRuntime {

    /**
     * 解析场景对应的供应商与模型（表优先，回退 AiProperties 默认）。
     *
     * @param workspaceId 工作空间 ID（可空，空则直接用默认）
     * @param scene       场景
     * @return 场景模型
     */
    SceneModel resolveScene(Long workspaceId, AiScene scene);

    /**
     * 非流式对话（无工具）。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param messages    Spring AI 消息列表
     * @param temperature 采样温度（可空）
     * @param maxTokens   最大生成 token 数（可空）
     * @return 完整文本（可能为空串）
     */
    String chat(Long workspaceId, AiScene scene, List<Message> messages,
                Double temperature, Integer maxTokens);

    /**
     * 流式对话（无工具）：内容增量逐段回调，异常回调 onError（不抛出）。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param messages    Spring AI 消息列表
     * @param temperature 采样温度（可空）
     * @param maxTokens   最大生成 token 数（可空）
     * @param onChunk     内容增量回调
     * @param onError     异常回调
     */
    void chatStream(Long workspaceId, AiScene scene, List<Message> messages,
                    Double temperature, Integer maxTokens,
                    Consumer<String> onChunk, Consumer<Throwable> onError);

    /**
     * 工具化非流式对话：ChatClient 自动多轮工具循环（工具执行中间态收集进 run.sink）。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param messages    Spring AI 消息列表
     * @param temperature 采样温度（可空）
     * @param maxTokens   最大生成 token 数（可空）
     * @param run         工具运行上下文（场景/业务上下文/收集器）
     * @return 完整文本
     */
    String chatWithTools(Long workspaceId, AiScene scene, List<Message> messages,
                         Double temperature, Integer maxTokens, ToolRun run);

    /**
     * 工具化流式对话：同上，内容增量逐段回调；工具事件经 run.sink 实时推送。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param messages    Spring AI 消息列表
     * @param temperature 采样温度（可空）
     * @param maxTokens   最大生成 token 数（可空）
     * @param run         工具运行上下文（场景/业务上下文/收集器）
     * @param onChunk     内容增量回调
     * @param onError     异常回调
     */
    void chatStreamWithTools(Long workspaceId, AiScene scene, List<Message> messages,
                             Double temperature, Integer maxTokens, ToolRun run,
                             Consumer<String> onChunk, Consumer<Throwable> onError);

    /**
     * 连通性测试：指定供应商+模型发一句 ping。
     *
     * @param providerName 供应商名（BAILIAN/DEEPSEEK）
     * @param model        模型名
     * @return 连通返回 true
     */
    boolean test(String providerName, String model);
}