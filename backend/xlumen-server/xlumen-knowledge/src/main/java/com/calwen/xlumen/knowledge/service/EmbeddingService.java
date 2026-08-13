package com.calwen.xlumen.knowledge.service;

import java.util.List;

/**
 * 向量化服务（F-0402/F-0404）：本模块内直接 HTTP 调用百炼兼容 embeddings 端点
 * （knowledge 不依赖 ai 模块，ai 依赖 knowledge）。key 缺失或调用失败抛
 * BizException(SERVICE_UNAVAILABLE)，由索引流水线标记任务失败。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface EmbeddingService {

    /**
     * 批量向量化（内部按 32 片/批拆分），返回与入参同序的向量列表。
     *
     * @param texts 待向量化文本列表
     * @return 向量列表（与 texts 一一对应）
     */
    List<List<Float>> embed(List<String> texts);
}
