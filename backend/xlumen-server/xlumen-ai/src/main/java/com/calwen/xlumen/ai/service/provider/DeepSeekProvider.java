package com.calwen.xlumen.ai.service.provider;

import com.calwen.xlumen.ai.config.AiProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 供应商（F-0501）：OpenAI 兼容端点，不支持向量化（embeddingModel 为空）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class DeepSeekProvider extends OpenAICompatibleProvider {

    public DeepSeekProvider(AiProperties props) {
        super(props.getDeepseekBaseUrl(), props.getDeepseekApiKey(), null);
    }

    @Override
    public String name() {
        return "DEEPSEEK";
    }
}
