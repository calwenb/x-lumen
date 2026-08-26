package com.calwen.xlumen.ai.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 供应商配置：绑定 .env 的 XLUMEN_ 变量（决策 D8 唯一配置载体，GLOBAL.md §6.2）。
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

    /** Agent 最大循环轮数：对话工具循环上限，用尽后最后一轮禁工具强制作答。 */
    @Value("${XLUMEN_AGENT_MAX_ROUNDS:5}")
    private int agentMaxRounds;

    /** AI 调用追踪：每千 Token 费用估算单价（元），用于 ai_call_log.est_cost。 */
    @Value("${XLUMEN_TRACE_COST_PER_1K:0.004}")
    private double traceCostPer1k;

    /** 单工具执行超时（毫秒）：超时返回错误信封，孤儿 future 结果丢弃。 */
    @Value("${XLUMEN_AGENT_TOOL_TIMEOUT_MILLIS:10000}")
    private long agentToolTimeoutMillis;

    /** 单请求工具调用总次数上限：超出部分截断并给错误信封。 */
    @Value("${XLUMEN_AGENT_MAX_TOOL_CALLS:8}")
    private int agentMaxToolCalls;

    /** 单工具结果截断长度：超长截断并附 truncated:true。 */
    @Value("${XLUMEN_AGENT_TOOL_RESULT_MAX_CHARS:8000}")
    private int agentToolResultMaxChars;

    /** 审校事实核对轮数上限：发布闸门路径总耗时应可控。 */
    @Value("${XLUMEN_REVIEWER_AGENT_MAX_ROUNDS:2}")
    private int reviewerAgentMaxRounds;

    /** 多步写作章节上限：大纲超过则回退单次生成模式。 */
    @Value("${XLUMEN_WRITING_MAX_CHAPTERS:8}")
    private int writingMaxChapters;

    /** 写作 RAG 增强（可选启用）：写作前检索知识库注入参考资料，结果携带引用证据。 */
    @Value("${XLUMEN_WRITING_RAG_ENABLED:true}")
    private boolean writingRagEnabled;
}
