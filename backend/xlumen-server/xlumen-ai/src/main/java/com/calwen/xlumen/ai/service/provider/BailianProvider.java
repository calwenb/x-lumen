package com.calwen.xlumen.ai.service.provider;

import com.calwen.xlumen.ai.config.AiProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼供应商（F-0501）：OpenAI 兼容端点（dashscope compatible-mode）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class BailianProvider extends OpenAICompatibleProvider {

    public BailianProvider(AiProperties props) {
        super(props.getBailianBaseUrl(), props.getBailianApiKey(), props.getBailianModelEmbedding());
    }

    @Override
    public String name() {
        return "BAILIAN";
    }
}
