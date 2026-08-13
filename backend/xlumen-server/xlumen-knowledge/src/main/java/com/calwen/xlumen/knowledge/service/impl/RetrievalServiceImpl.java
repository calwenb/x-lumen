package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.service.EmbeddingService;
import com.calwen.xlumen.knowledge.service.RetrievalService;
import com.calwen.xlumen.knowledge.service.VectorStore;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索服务实现（F-0404/F-0407）：Embedding(query) → VectorStore.search。
 * 查询为空或向量化为空时返回空列表（Noop 降级亦返回空）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    /** 单次检索返回条数上限。 */
    private static final int MAX_TOP_K = 50;

    @Resource
    private EmbeddingService embeddingService;
    @Resource
    private VectorStore vectorStore;

    @Override
    public List<SearchResultDTO> search(SearchRequestDTO request) {
        if (request == null || StrUtil.isBlank(request.getQuery())) {
            return List.of();
        }
        int topK = Math.max(1, Math.min(request.getTopK(), MAX_TOP_K));
        List<List<Float>> embeddings = embeddingService.embed(List.of(request.getQuery()));
        if (embeddings.isEmpty() || embeddings.get(0).isEmpty()) {
            return List.of();
        }
        return vectorStore.search(embeddings.get(0), request.getWorkspaceId(),
                request.getVisibilityScope(), request.getArticleId(), topK);
    }
}
