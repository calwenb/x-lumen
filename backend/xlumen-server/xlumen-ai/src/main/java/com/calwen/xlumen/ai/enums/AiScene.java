package com.calwen.xlumen.ai.enums;

/**
 * AI 场景：Writing/Reviewer/问答/摘要/SEO 各自独立配置模型与参数（BACKEND.md §14）。
 * Writing 与 Reviewer 必须模型异源。向量化（Embedding）不属于场景配置，
 * 由 knowledge 模块直接读各环境 profile 的 xlumen.bailian.model-embedding（见 EmbeddingServiceImpl），不在本枚举。
 *
 * @author calwen
 * @date 2026/8/13
 */
public enum AiScene {

    /** AI 写作：输入主题/草稿/完整文章，输出完整文章。 */
    WRITING,
    /** AI 审校：独立模型，结构化输出严重度/位置/证据/建议。 */
    REVIEWER,
    /** AI 对话问答：RAG 检索增强生成，引用溯源。 */
    QA,
    /** 摘要生成。 */
    SUMMARY,
    /** SEO 优化：标题/关键词/描述。 */
    SEO
}
