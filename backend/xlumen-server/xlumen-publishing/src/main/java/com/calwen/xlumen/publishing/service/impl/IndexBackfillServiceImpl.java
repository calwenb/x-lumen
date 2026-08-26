package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.vo.IndexStatusVO;
import com.calwen.xlumen.publishing.dto.ReindexAllVO;
import com.calwen.xlumen.publishing.service.IndexBackfillService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 索引补跑编排实现：读取当前空间已发布知识正文，经 KnowledgeApi 强制重建索引；
 * 全量补跑（reindexAll）逐条执行、单条失败不中断。
 *
 * @author calwen
 * @date 2026/8/17
 */
@Service
public class IndexBackfillServiceImpl implements IndexBackfillService {

    private static final Logger log = LoggerFactory.getLogger(IndexBackfillServiceImpl.class);

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    @Override
    public IndexStatusVO reindex(Long knowledgeId) {
        Long workspaceId = requireWorkspace();
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        return doReindex(workspaceId, knowledge);
    }

    @Override
    public ReindexAllVO reindexAll() {
        Long workspaceId = requireWorkspace();
        Long userId = WorkspaceContext.userId();
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(userId);
        if (visibleKbIds == null || visibleKbIds.isEmpty()) {
            return emptyResult();
        }
        ContentPageResult<PublishedKnowledgeDTO> page = contentApi.listPublished(null,
                com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO.builder()
                        .visibleKbIds(visibleKbIds).pageNo(1L).pageSize(100L).build());
        List<ReindexAllVO.FailedItem> failed = new ArrayList<>();
        long ok = 0;
        long total = 0;
        for (PublishedKnowledgeDTO published : page.getRecords()) {
            EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, published.getId());
            if (knowledge == null || KnowledgeStatus.of(knowledge.getStatus()) != KnowledgeStatus.PUBLISHED) {
                continue;
            }
            total++;
            try {
                doReindex(workspaceId, knowledge);
                ok++;
            } catch (Exception e) {
                log.warn("全量重建索引失败 knowledgeId={}", published.getId(), e);
                failed.add(ReindexAllVO.FailedItem.builder()
                        .knowledgeId(published.getId()).reason(safeMessage(e)).build());
            }
        }
        return ReindexAllVO.builder().total(total).ok(ok).failed(failed).build();
    }

    /** 单条重建核心（正文已取回时复用，避免全量补跑重复读取）。 */
    private IndexStatusVO doReindex(Long workspaceId, EditorKnowledgeDTO knowledge) {
        // 发布即索引（决策 D13）：只有已发布知识存在索引，其余状态无可补跑对象
        if (KnowledgeStatus.of(knowledge.getStatus()) != KnowledgeStatus.PUBLISHED) {
            throw new BizException(ErrorCode.CONFLICT, "仅已发布知识可重建索引");
        }
        knowledgeApi.reindexKnowledge(IndexRequestDTO.builder()
                .workspaceId(workspaceId)
                .kbId(knowledge.getKbId())
                .knowledgeId(knowledge.getId())
                .version(knowledge.getVersion())
                .title(knowledge.getTitle())
                .content(knowledge.getContent())
                .build());
        return knowledgeApi.getIndexStatus(workspaceId, knowledge.getId());
    }

    private Long requireWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }

    private ReindexAllVO emptyResult() {
        return ReindexAllVO.builder().total(0).ok(0).failed(List.of()).build();
    }

    private String safeMessage(Throwable e) {
        String msg = e.getMessage();
        if (StrUtil.isBlank(msg)) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}