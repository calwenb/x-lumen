package com.calwen.xlumen.ai.service.provider;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mock 供应商（F-0501）：返回固定文本与固定向量，available 恒 true。
 * 仅用于测试与无密钥兜底（网关在供应商缺密钥时回退本实现）。
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

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String chat(ProviderChatRequest request) {
        return "这是一段模拟回复。";
    }

    @Override
    public void chatStream(ProviderChatRequest request, Consumer<String> onChunk, Consumer<Throwable> onError) {
        try {
            for (int i = 0; i < FIXED_TEXT.length(); i++) {
                onChunk.accept(String.valueOf(FIXED_TEXT.charAt(i)));
                Thread.sleep(10);
            }
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
