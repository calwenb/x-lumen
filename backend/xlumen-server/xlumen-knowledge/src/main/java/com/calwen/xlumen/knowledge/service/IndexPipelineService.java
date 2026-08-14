package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.vo.IndexStatusVO;

/**
 * 索引流水线（F-0402/F-0403）：发布即索引的编排入口——清洗、切片、幂等、Embedding、
 * 写向量、激活新版本、清理旧版本。异步执行失败不影响发布本身。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface IndexPipelineService {

    /**
     * 索引知识（F-0402）：清洗→切片→幂等检查→Embedding→写向量→激活→旧版本清理。
     *
     * @param request 索引请求（含正文快照）
     */
    void indexKnowledge(IndexRequestDTO request);

    /**
     * 移除知识索引（删除/下架同步出索引，F-0402）。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     */
    void removeKnowledge(Long workspaceId, Long knowledgeId);

    /**
     * 查询知识当前索引状态（F-0404）；未索引返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @return 索引状态或 null
     */
    IndexStatusVO getIndexStatus(Long workspaceId, Long knowledgeId);
}
