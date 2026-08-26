package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.knowledge.vo.IndexStatusVO;
import com.calwen.xlumen.publishing.dto.ReindexAllVO;

/**
 * 索引补跑编排：knowledge 模块依赖方向受限无法自取正文，
 * 由本模块（依赖 content + knowledge）读取已发布知识正文后强制重建索引。
 *
 * @author calwen
 * @date 2026/8/17
 */
public interface IndexBackfillService {

    /**
     * 强制重建已发布知识的索引：失效旧切片/版本后重跑流水线（同步执行，返回最新索引状态）。
     *
     * @param knowledgeId 知识 ID
     * @return 重建后的索引状态
     */
    IndexStatusVO reindex(Long knowledgeId);

    /**
     * 全量重建当前空间已发布知识索引（逐条补跑，单条失败不中断），返回汇总。
     *
     * @return 重建汇总
     */
    ReindexAllVO reindexAll();
}
