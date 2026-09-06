package com.calwen.xlumen.knowledge.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识模块 AI 配置：绑定各环境 profile 的 xlumen.bailian.* 键（决策 D29）。
 * 本模块不依赖 ai 模块（ai 依赖 knowledge），故在此独立声明百炼 embeddings 相关配置。
 * 注：@Value 键名与 profile YAML 的小写点号键一致（如 xlumen.bailian.api-key）；键缺失即启动失败（不设默认值，强制配置完整）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class KnowledgeAiProperties {

    /** 百炼 API Key（不入日志/响应）。 */
    @Value("${xlumen.bailian.api-key}")
    private String bailianApiKey;

    /** 百炼兼容模式 Base URL（OpenAI 兼容端点）。 */
    @Value("${xlumen.bailian.base-url}")
    private String bailianBaseUrl;

    /** 百炼 Embedding 模型。 */
    @Value("${xlumen.bailian.model-embedding}")
    private String bailianModelEmbedding;
}
