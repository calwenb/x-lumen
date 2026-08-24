package com.calwen.xlumen.ai.service.provider;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Mock 供应商（F-0501）：返回固定文本与固定向量，available 恒 true。
 * 仅用于测试与无密钥兜底（网关在供应商缺密钥时回退本实现）。
 * IDEA-025 F-0708：支持注入脚本队列（按调用次序出队，典型脚本「第一轮返 tool_calls → 第二轮返正文」），
 * 无脚本时保持现状固定行为——本地全链路离线测试的基础（AGENTS.md 铁律：测试不调付费模型）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class MockProvider implements ModelProvider {

    /** 固定回复：带标题的 Markdown 文本，便于写作场景解析。 */
    private static final String FIXED_TEXT = "# 模拟文章标题\n\n这是一段模拟生成的正文内容，用于无密钥环境下的功能测试。\n\n## 小结\n模拟内容结束。";

    /** 固定向量（16 维）。 */
    private static final List<Float> FIXED_VECTOR = Arrays.asList(
            0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f,
            0.9f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f);

    /** 脚本队列：按调用次序出队（chat 与 chatStream 共用），无脚本时走固定行为。 */
    private final Queue<ProviderChatResult> script = new ConcurrentLinkedQueue<>();

    /** 注入脚本队列（可多段追加）。 */
    public void enqueueScript(ProviderChatResult... results) {
        if (results != null) {
            script.addAll(Arrays.asList(results));
        }
    }

    /** 清空脚本（恢复固定行为）。 */
    public void clearScript() {
        script.clear();
    }

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ProviderChatResult chat(ProviderChatRequest request) {
        ProviderChatResult next = script.poll();
        if (next != null) {
            return next;
        }
        return ProviderChatResult.builder().content("这是一段模拟回复。").build();
    }

    @Override
    public void chatStream(ProviderChatRequest request, StreamCallback callback, Consumer<Throwable> onError) {
        ProviderChatResult next = script.poll();
        if (next != null) {
            if (StrUtil.isNotBlank(next.getContent())) {
                for (int i = 0; i < next.getContent().length(); i++) {
                    callback.onContent(String.valueOf(next.getContent().charAt(i)));
                }
            }
            callback.onResult(next);
            return;
        }
        try {
            for (int i = 0; i < FIXED_TEXT.length(); i++) {
                callback.onContent(String.valueOf(FIXED_TEXT.charAt(i)));
                Thread.sleep(10);
            }
            callback.onResult(ProviderChatResult.builder().content(FIXED_TEXT).build());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onError.accept(e);
        }
    }

    @Override
    public List<Float> embed(String text) {
        return FIXED_VECTOR;
    }
}