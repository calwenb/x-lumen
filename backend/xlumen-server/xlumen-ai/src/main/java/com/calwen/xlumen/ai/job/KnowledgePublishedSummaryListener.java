package com.calwen.xlumen.ai.job;

import com.calwen.xlumen.ai.service.EnhanceService;
import com.calwen.xlumen.common.event.KnowledgePublishedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 发布即摘要事件监听（F-0808）：监听 publishing 发布的进程内 KnowledgePublishedEvent，
 * 用 ai 模块线程池 aiTaskExecutor 异步生成知识摘要并落 ai_enhance_result（scene=SUMMARY）。
 * 失败仅 log.warn 降级，绝不影响发布主流程（与 knowledge 模块 KnowledgePublishedEventListener 同款写法）。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Slf4j
@Component
public class KnowledgePublishedSummaryListener {

    @Resource
    private EnhanceService enhanceService;

    @Resource(name = "aiTaskExecutor")
    private ThreadPoolTaskExecutor aiTaskExecutor;

    /**
     * 消费知识发布事件，异步生成摘要（workspaceId/knowledgeId/title/content 取自事件正文快照）。
     *
     * @param event 知识发布成功事件（含正文快照）
     */
    @EventListener
    public void onKnowledgePublished(KnowledgePublishedEvent event) {
        aiTaskExecutor.execute(() -> {
            try {
                enhanceService.generateAndStoreSummary(event.getWorkspaceId(), event.getKnowledgeId(),
                        event.getTitle(), event.getContent());
            } catch (Exception e) {
                // 摘要为非关键增强：失败降级（下次发布或手工增强可补），不影响发布主流程
                log.warn("发布即摘要生成失败（降级忽略）：knowledgeId={}", event.getKnowledgeId(), e);
            }
        });
    }
}
