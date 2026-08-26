package com.calwen.xlumen.ai.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 脚本化聊天模型（D20 全量迁移）：以 Spring AI ChatModel 形态替代原 MockProvider——
 * 无密钥兜底与离线测试的基座（AGENTS.md 铁律：测试不调付费模型）。
 * 支持注入响应脚本队列（按调用次序出队，典型脚本「第一轮返 assistant.tool_calls → 第二轮返正文」），
 * 无脚本时返回固定文本。ChatClient 的工具循环在本模型上照常运转（模型不发起工具调用则单轮结束）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class ScriptedChatModel implements ChatModel {

    /** 固定回复：带标题的 Markdown 文本，便于写作场景解析。 */
    private static final String FIXED_TEXT = "# 模拟文章标题\n\n这是一段模拟生成的正文内容，用于无密钥环境下的功能测试。\n\n## 小结\n模拟内容结束。";

    /** 响应脚本队列：按调用次序出队（call 与 stream 共用），空则固定行为。 */
    private final Queue<ChatResponse> script = new ConcurrentLinkedQueue<>();

    /** 注入响应脚本（可多段追加）。 */
    public void enqueueScript(ChatResponse... responses) {
        if (responses != null) {
            script.addAll(List.of(responses));
        }
    }

    /** 清空脚本（恢复固定行为）。 */
    public void clearScript() {
        script.clear();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return next();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(next());
    }

    @Override
    public ChatOptions getOptions() {
        return OpenAiChatOptions.builder().build();
    }

    private ChatResponse next() {
        ChatResponse scripted = script.poll();
        return scripted != null ? scripted : fixedResponse();
    }

    private ChatResponse fixedResponse() {
        AssistantMessage message = AssistantMessage.builder().content(FIXED_TEXT).build();
        return new ChatResponse(List.of(new Generation(message)));
    }
}