package com.calwen.xlumen.knowledge.job;

import com.calwen.xlumen.common.event.KnowledgePublishedEvent;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * 发布即索引事件监听（F-0402，决策 D13）：监听 publishing 发布的进程内 KnowledgePublishedEvent，
 * 用本模块线程池异步执行索引流水线（不依赖 @Async/@EnableAsync）。任务失败仅记录日志，不影响发布。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
@Component
public class KnowledgePublishedEventListener {

    @Resource
    private IndexPipelineService indexPipelineService;

    @Resource(name = "indexExecutor")
    private ExecutorService indexExecutor;

    /**
     * 消费知识发布事件，异步执行发布即索引流水线。
     *
     * @param event 知识发布成功事件（含正文快照）
     */
    @EventListener
    public void onKnowledgePublished(KnowledgePublishedEvent event) {
        indexExecutor.execute(() -> {
            try {
                IndexRequestDTO request = IndexRequestDTO.builder()
                        .workspaceId(event.getWorkspaceId())
                        .knowledgeId(event.getKnowledgeId())
                        .version(event.getVersion())
                        .title(event.getTitle())
                        .content(event.getContent())
                        .visibility(event.getVisibility())
                        .build();
                indexPipelineService.indexKnowledge(request);
            } catch (Exception e) {
                log.error("发布即索引任务失败：knowledgeId={}", event.getKnowledgeId(), e);
            }
        });
    }
}
