package com.calwen.xlumen.ai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 供应商配置：绑定各环境 profile 的 xlumen.* 键（决策 D29，GLOBAL.md §6.2）。
 * 各环境 profile（application-<env>.yml）提供服务器级默认密钥与默认模型；业务级场景配置在 ai_scene_config 表（管理面 A03），运行时表优先、profile 回退。
 * 注：@Value 键名与 profile YAML 的小写点号键一致（如 xlumen.bailian.api-key）；键缺失即启动失败（不设默认值，强制配置完整）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@ConfigurationProperties(prefix = "xlumen")
public class AiProperties {

    /** 百炼 API Key（不入日志/响应）。 */
    @Value("${xlumen.bailian.api-key}")
    private String bailianApiKey;

    /** 百炼兼容模式 Base URL（OpenAI 兼容端点）。 */
    @Value("${xlumen.bailian.base-url}")
    private String bailianBaseUrl;

    /** 百炼默认模型：写作。 */
    @Value("${xlumen.bailian.model-writing}")
    private String bailianModelWriting;

    /** 百炼默认模型：审校。 */
    @Value("${xlumen.bailian.model-reviewer}")
    private String bailianModelReviewer;

    /** 百炼默认模型：问答。 */
    @Value("${xlumen.bailian.model-qa}")
    private String bailianModelQa;

    /** 百炼默认模型：摘要。 */
    @Value("${xlumen.bailian.model-summary}")
    private String bailianModelSummary;

    /** 百炼默认模型：图片讲解（视觉模型，选便宜档）。 */
    @Value("${xlumen.bailian.model-vision}")
    private String bailianModelVision;

    /** DeepSeek API Key（不入日志/响应）。 */
    @Value("${xlumen.deepseek.api-key}")
    private String deepseekApiKey;

    /** DeepSeek Base URL。 */
    @Value("${xlumen.deepseek.base-url}")
    private String deepseekBaseUrl;

    /** DeepSeek 默认模型：写作。 */
    @Value("${xlumen.deepseek.model-writing}")
    private String deepseekModelWriting;

    /** DeepSeek 默认模型：审校。 */
    @Value("${xlumen.deepseek.model-reviewer}")
    private String deepseekModelReviewer;

    /** DeepSeek 默认模型：问答。 */
    @Value("${xlumen.deepseek.model-qa}")
    private String deepseekModelQa;

    /** DeepSeek 默认模型：摘要。 */
    @Value("${xlumen.deepseek.model-summary}")
    private String deepseekModelSummary;

    /** Agent 最大循环轮数：对话工具循环上限，用尽后最后一轮禁工具强制作答。 */
    @Value("${xlumen.agent-max-rounds}")
    private int agentMaxRounds;

    /** AI 调用追踪：每千 Token 费用估算单价（元），用于 ai_call_log.est_cost。 */
    @Value("${xlumen.trace-cost-per-1k}")
    private double traceCostPer1k;

    /** 单工具执行超时（毫秒）：超时返回错误信封，孤儿 future 结果丢弃。 */
    @Value("${xlumen.agent-tool-timeout-millis}")
    private long agentToolTimeoutMillis;

    /** 单请求工具调用总次数上限：超出部分截断并给错误信封。 */
    @Value("${xlumen.agent-max-tool-calls}")
    private int agentMaxToolCalls;

    /** 单工具结果截断长度：超长截断并附 truncated:true。 */
    @Value("${xlumen.agent-tool-result-max-chars}")
    private int agentToolResultMaxChars;

    /** 审校事实核对轮数上限：发布闸门路径总耗时应可控。 */
    @Value("${xlumen.reviewer-agent-max-rounds}")
    private int reviewerAgentMaxRounds;

    /** 多步写作章节上限：大纲超过则回退单次生成模式。 */
    @Value("${xlumen.writing-max-chapters}")
    private int writingMaxChapters;

    /** 写作 RAG 增强（可选启用）：写作前检索知识库注入参考资料，结果携带引用证据。 */
    @Value("${xlumen.writing-rag-enabled}")
    private boolean writingRagEnabled;
}
