package com.calwen.xlumen.ai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 供应商配置（F-0501）：绑定 .env 的 XLUMEN_ 变量（决策 D8 唯一配置载体，GLOBAL.md §6.2）。
 * .env 提供服务器级默认密钥与默认模型；业务级场景配置在 ai_scene_config 表（管理面 A03），运行时表优先、.env 回退。
 * 注：Boot 4 的 Binder 对 .env 导入的大写属性不做 relaxed binding，改用 @Value 显式占位符绑定（与 application.yml 同源可靠）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class AiProperties {

    /** 百炼 API Key（不入日志/响应）。 */
    @Value("${XLUMEN_BAILIAN_API_KEY:}")
    private String bailianApiKey;

    /** 百炼兼容模式 Base URL（OpenAI 兼容端点）。 */
    @Value("${XLUMEN_BAILIAN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String bailianBaseUrl;

    /** 百炼默认模型：写作。 */
    @Value("${XLUMEN_BAILIAN_MODEL_WRITING:qwen-plus}")
    private String bailianModelWriting;

    /** 百炼默认模型：审校。 */
    @Value("${XLUMEN_BAILIAN_MODEL_REVIEWER:qwen-plus}")
    private String bailianModelReviewer;

    /** 百炼默认模型：问答。 */
    @Value("${XLUMEN_BAILIAN_MODEL_QA:qwen-plus}")
    private String bailianModelQa;

    /** 百炼默认模型：摘要。 */
    @Value("${XLUMEN_BAILIAN_MODEL_SUMMARY:qwen-plus}")
    private String bailianModelSummary;

    /** 百炼默认模型：Embedding。 */
    @Value("${XLUMEN_BAILIAN_MODEL_EMBEDDING:text-embedding-v4}")
    private String bailianModelEmbedding;

    /** DeepSeek API Key（不入日志/响应）。 */
    @Value("${XLUMEN_DEEPSEEK_API_KEY:}")
    private String deepseekApiKey;

    /** DeepSeek Base URL。 */
    @Value("${XLUMEN_DEEPSEEK_BASE_URL:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    /** DeepSeek 默认模型：写作。 */
    @Value("${XLUMEN_DEEPSEEK_MODEL_WRITING:deepseek-chat}")
    private String deepseekModelWriting;

    /** DeepSeek 默认模型：审校。 */
    @Value("${XLUMEN_DEEPSEEK_MODEL_REVIEWER:deepseek-chat}")
    private String deepseekModelReviewer;

    /** DeepSeek 默认模型：问答。 */
    @Value("${XLUMEN_DEEPSEEK_MODEL_QA:deepseek-chat}")
    private String deepseekModelQa;

    /** DeepSeek 默认模型：摘要。 */
    @Value("${XLUMEN_DEEPSEEK_MODEL_SUMMARY:deepseek-chat}")
    private String deepseekModelSummary;
}
