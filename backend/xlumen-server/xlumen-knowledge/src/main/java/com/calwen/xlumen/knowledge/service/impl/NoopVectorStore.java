package com.calwen.xlumen.knowledge.service.impl;

import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.dto.Chunk;
import com.calwen.xlumen.knowledge.service.VectorStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 向量库 Noop 降级实现（MVP 环境无 Milvus）：不写向量、检索返回空列表；
 * 索引版本状态虽标记 ACTIVE（元数据已落库），但向量未激活（kb_chunk.vector_id 留空）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
public class NoopVectorStore implements VectorStore {

    @Override
    public void index(IndexRequestDTO request, List<Chunk> chunks) {
        log.debug("NoopVectorStore：跳过向量写入，knowledgeId={}, version={}, 切片数={}",
                request.getKnowledgeId(), request.getVersion(), chunks == null ? 0 : chunks.size());
    }

    @Override
    public void delete(Long workspaceId, Long knowledgeId) {
        log.debug("NoopVectorStore：跳过向量删除，workspaceId={}, knowledgeId={}", workspaceId, knowledgeId);
    }

    @Override
    public List<SearchResultDTO> search(List<Float> queryEmbedding, Long workspaceId, String visibilityScope,
                                        Long knowledgeId, int topK) {
        return List.of();
    }
}
