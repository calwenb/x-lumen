package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.vo.IndexStatusVO;
import com.calwen.xlumen.publishing.service.IndexBackfillService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 索引补跑编排实现（BUG-004）：读取当前空间已发布知识正文，经 KnowledgeApi 强制重建索引。
 *
 * @author calwen
 * @date 2026/8/17
 */
@Service
public class IndexBackfillServiceImpl implements IndexBackfillService {

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    @Override
    public IndexStatusVO reindex(Long knowledgeId) {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        // 发布即索引（决策 D13）：只有已发布知识存在索引，其余状态无可补跑对象
        if (KnowledgeStatus.of(knowledge.getStatus()) != KnowledgeStatus.PUBLISHED) {
            throw new BizException(ErrorCode.CONFLICT, "仅已发布知识可重建索引");
        }
        knowledgeApi.reindexKnowledge(IndexRequestDTO.builder()
                .workspaceId(workspaceId)
                .kbId(knowledge.getKbId())
                .knowledgeId(knowledgeId)
                .version(knowledge.getVersion())
                .title(knowledge.getTitle())
                .content(knowledge.getContent())
                .build());
        return knowledgeApi.getIndexStatus(workspaceId, knowledgeId);
    }
}
