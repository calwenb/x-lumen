package com.calwen.xlumen.publishing.job;

import com.calwen.xlumen.knowledge.event.KbVisibilityChangedEvent;
import com.calwen.xlumen.publishing.service.HotKnowledgeCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 知识库可见性变更事件监听（KB-3，F-0308）：库公开↔私有切换后即时失效该库热点缓存
 * （方案 §3.4 缓存分片，可见性变更按维度失效），保证公开流/详情不再命中旧可见性内容。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Slf4j
@Component
public class KnowledgeBaseVisibilityEventListener {

    @Resource
    private HotKnowledgeCacheService hotKnowledgeCacheService;

    @EventListener
    public void onKbVisibilityChanged(KbVisibilityChangedEvent event) {
        try {
            hotKnowledgeCacheService.evictByKb(event.getKbId());
            log.info("知识库可见性变更缓存失效完成：kbId={}, visibility={}", event.getKbId(), event.getVisibility());
        } catch (Exception e) {
            log.warn("知识库可见性变更缓存失效失败（降级忽略）：kbId={}", event.getKbId(), e);
        }
    }
}
