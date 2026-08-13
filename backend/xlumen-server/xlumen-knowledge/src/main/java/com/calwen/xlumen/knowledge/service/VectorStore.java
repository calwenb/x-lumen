package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.dto.Chunk;

import java.util.List;

/**
 * 向量库抽象（F-0402/F-0407）：屏蔽 Milvus 与 Noop 降级差异，统一向流水线/检索暴露
 * 写向量、删向量、检索三个能力。实现由 VectorStoreAutoConfiguration 探测装配。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface VectorStore {

    /**
     * 写向量 + 元数据（切片维度：标题/段落锚点/可见性等，供检索结果组装与权限过滤）。
     *
     * @param request 索引请求（含文章元数据）
     * @param chunks  切片列表（含已回填的 embedding）
     */
    void index(IndexRequestDTO request, List<Chunk> chunks);

    /**
     * 删除文章全部向量条目（删除/下架同步出索引，F-0402）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     */
    void delete(Long workspaceId, Long articleId);

    /**
     * 向量检索（F-0404/F-0407）：按可见性范围过滤，返回按分数降序的结果。
     *
     * @param queryEmbedding 查询向量
     * @param workspaceId    工作空间 ID
     * @param visibilityScope 可见性范围：PUBLIC_ONLY 仅公开 / ALL 含私有
     * @param articleId      文章级过滤（可空）
     * @param topK           返回条数
     * @return 检索结果列表
     */
    List<SearchResultDTO> search(List<Float> queryEmbedding, Long workspaceId, String visibilityScope,
                                 Long articleId, int topK);
}
