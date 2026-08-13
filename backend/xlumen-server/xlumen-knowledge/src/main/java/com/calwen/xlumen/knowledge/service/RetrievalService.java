package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;

import java.util.List;

/**
 * 检索服务（F-0404/F-0407）：Embedding(query) → VectorStore.search → 组装结果。
 * Noop 降级时向量检索返回空列表；可见性过滤由 visibilityScope 入参决定。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface RetrievalService {

    /**
     * 向量检索（F-0404/F-0407）。
     *
     * @param request 检索请求
     * @return 检索结果（按分数降序）
     */
    List<SearchResultDTO> search(SearchRequestDTO request);
}
