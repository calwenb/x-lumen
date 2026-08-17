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
     * 索引知识（F-0402）：清洗->切片->幂等检查->Embedding->写向量->激活->旧版本清理。
     *
     * @param request 索引请求（含正文快照）
     */
    void indexKnowledge(IndexRequestDTO request);

    /**
     * 强制重建索引（BUG-004 存量补跑）：先将已有切片置失效、版本置 STALE，再绕过内容 hash
     * 幂等检查走完整流水线。用于 Noop 降级期间落库的历史版本补写向量（向量库不可用时抛错回滚状态可重试）。
     *
     * @param request 索引请求（含正文快照）
     */
    void reindex(IndexRequestDTO request);

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
