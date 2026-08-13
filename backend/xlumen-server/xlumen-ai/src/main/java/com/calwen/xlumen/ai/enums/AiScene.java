package com.calwen.xlumen.ai.enums;

/**
 * AI 场景（F-0502）：Writing/Reviewer/问答/摘要/Embedding 各自独立配置模型与参数（BACKEND.md §14）。
 * Writing 与 Reviewer 必须模型异源（F-0604 审校独立性）；Embedding 供 knowledge 索引流水线调用。
 *
 * @author calwen
 * @date 2026/8/13
 */
public enum AiScene {

    /** AI 写作（F-0601）：输入主题/草稿/完整文章，输出完整文章。 */
    WRITING,
    /** AI 审校（F-0604）：独立模型，结构化输出严重度/位置/证据/建议。 */
    REVIEWER,
    /** AI 对话问答（F-0701/F-0702）：RAG 检索增强生成，引用溯源。 */
    QA,
    /** 摘要生成（F-0801）。 */
    SUMMARY,
    /** SEO 优化（F-0802）：标题/关键词/描述。 */
    SEO,
    /** 向量化（F-0402 索引流水线）。 */
    EMBEDDING
}
