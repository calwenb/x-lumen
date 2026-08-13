package com.calwen.xlumen.knowledge.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识模块 AI 配置（F-0402/F-0404）：绑定 .env 的 XLUMEN_BAILIAN_* 变量（决策 D8 唯一配置载体）。
 * 本模块不依赖 ai 模块（ai 依赖 knowledge），故在此独立声明百炼 embeddings 相关配置。
 * 注：Boot 4 的 Binder 对 .env 导入的大写属性不做 relaxed binding，改用 @Value 显式占位符绑定。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class KnowledgeAiProperties {

    /** 百炼 API Key（不入日志/响应）。 */
    @Value("${XLUMEN_BAILIAN_API_KEY:}")
    private String bailianApiKey;

    /** 百炼兼容模式 Base URL（OpenAI 兼容端点）。 */
    @Value("${XLUMEN_BAILIAN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String bailianBaseUrl;

    /** 百炼 Embedding 模型。 */
    @Value("${XLUMEN_BAILIAN_MODEL_EMBEDDING:text-embedding-v4}")
    private String bailianModelEmbedding;
}
