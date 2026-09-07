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
import com.calwen.xlumen.publishing.dto.ReindexPlatformVO;
import com.calwen.xlumen.publishing.service.IndexBackfillService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /** 全平台补跑游标分页每页条数。 */
    private static final int PLATFORM_PAGE_SIZE = 100;
    /** 全平台补跑失败明细最多保留条数（计数不受截断影响）。 */
    private static final int PLATFORM_FAILED_DETAIL_CAP = 100;

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    /** 全平台补跑专用单线程执行器：逐条串行，既避免同步批量 embedding 撑爆 HTTP 超时，也避免并发打满付费模型限流。 */
    private final ExecutorService platformBackfillExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "knowledge-reindex-platform");
        thread.setDaemon(true);
        return thread;
    });

    /** 补跑任务进度共享态（任务线程单写、状态接口读；finishedAt 非空=已结束）。 */
    private volatile PlatformBackfillTask platformTask;

    @PreDestroy
    public void shutdownPlatformBackfill() {
        platformBackfillExecutor.shutdownNow();
    }

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

    @Override
    public ReindexPlatformVO reindexAllPlatform() {
        synchronized (this) {
            PlatformBackfillTask running = platformTask;
            if (running != null && running.finishedAt == null) {
                return toVO(running, false);
            }
            PlatformBackfillTask task = new PlatformBackfillTask();
            task.startedAt = LocalDateTime.now();
            task.total = contentApi.countPublishedPlatform();
            platformTask = task;
            platformBackfillExecutor.execute(() -> runPlatformBackfill(task));
            log.info("全平台索引补跑已触发：total={}", task.total);
            return toVO(task, true);
        }
    }

    @Override
    public ReindexPlatformVO reindexAllPlatformStatus() {
        PlatformBackfillTask task = platformTask;
        if (task == null) {
            return ReindexPlatformVO.builder().started(false).running(false).failed(List.of()).build();
        }
        return toVO(task, false);
    }

    /** 任务主体：id 游标遍历全空间已发布知识逐条强制重建，单条失败不中断；finally 兜底置结束时间。 */
    private void runPlatformBackfill(PlatformBackfillTask task) {
        long cursor = 0L;
        try {
            while (true) {
                List<EditorKnowledgeDTO> batch = contentApi.listPublishedSnapshotsAfter(cursor, PLATFORM_PAGE_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (EditorKnowledgeDTO knowledge : batch) {
                    cursor = Math.max(cursor, knowledge.getId());
                    task.processed++;
                    try {
                        doReindex(knowledge.getWorkspaceId(), knowledge);
                        task.ok++;
                    } catch (Exception e) {
                        log.warn("全平台索引补跑失败 knowledgeId={}", knowledge.getId(), e);
                        if (task.failed.size() < PLATFORM_FAILED_DETAIL_CAP) {
                            task.failed.add(ReindexAllVO.FailedItem.builder()
                                    .knowledgeId(knowledge.getId()).reason(safeMessage(e)).build());
                        }
                    }
                }
                if (batch.size() < PLATFORM_PAGE_SIZE) {
                    break;
                }
            }
        } finally {
            task.finishedAt = LocalDateTime.now();
            log.info("全平台索引补跑结束：processed={}, ok={}, failed={}",
                    task.processed, task.ok, task.processed - task.ok);
        }
    }

    /** 任务进度快照转视图（started=本次触发是否真正启动了任务）。 */
    private ReindexPlatformVO toVO(PlatformBackfillTask task, boolean started) {
        return ReindexPlatformVO.builder()
                .started(started)
                .running(task.finishedAt == null)
                .total(task.total)
                .processed(task.processed)
                .ok(task.ok)
                .failedCount(task.processed - task.ok)
                .failed(List.copyOf(task.failed))
                .startedAt(task.startedAt)
                .finishedAt(task.finishedAt)
                .build();
    }

    /** 全平台补跑任务内存态（重启即失，仅覆盖单次补跑窗口）。 */
    private static class PlatformBackfillTask {
        private volatile long total;
        private volatile long processed;
        private volatile long ok;
        private final List<ReindexAllVO.FailedItem> failed = Collections.synchronizedList(new ArrayList<>());
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;
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