package com.calwen.xlumen.content.job;

import com.calwen.xlumen.content.service.impl.ContentApiImpl;
import com.calwen.xlumen.knowledge.event.KbDirectoryDeletedEvent;
import com.calwen.xlumen.knowledge.event.KbPurgedEvent;
import com.calwen.xlumen.knowledge.event.KbRecycleStatusEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 知识库联动事件监听（KB-3，F-0305/F-0309）：knowledge 模块在库/目录/回收站状态变更时发布进程内事件，
 * 本监听负责 cnt_knowledge 侧的连带操作——删库连带软删、恢复连带恢复、目录删除知识上挂、
 * 库彻底删除物理级联删知识（方案 §7.2）。任务失败仅记录日志，不影响库侧状态主流程。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Slf4j
@Component
public class KnowledgeBaseLinkEventListener {

    @Resource
    private ContentApiImpl contentApi;

    /**
     * 库进回收站（status=1）连带软删库内知识；库恢复（status=0）连带恢复。
     *
     * @param event 库回收站状态事件
     */
    @EventListener
    public void onKbRecycleStatus(KbRecycleStatusEvent event) {
        try {
            if (event.getStatus() != null && event.getStatus() == 1) {
                contentApi.softDeleteKnowledgeByKb(event.getWorkspaceId(), event.getKbId());
                log.info("知识库连带软删完成：kbId={}", event.getKbId());
            } else {
                contentApi.restoreKnowledgeByKb(event.getWorkspaceId(), event.getKbId());
                log.info("知识库连带恢复完成：kbId={}", event.getKbId());
            }
        } catch (Exception e) {
            log.error("知识库连带回收/恢复失败：kbId={}", event.getKbId(), e);
        }
    }

    /**
     * 目录删除：目录子树下知识上挂到目标目录（方案 §7.2「删除目录时知识上挂父目录」）。
     *
     * @param event 目录删除事件（含子树 ID 集合与目标目录）
     */
    @EventListener
    public void onKbDirectoryDeleted(KbDirectoryDeletedEvent event) {
        try {
            contentApi.relocateKnowledgeByDirectories(event.getWorkspaceId(), event.getKbId(),
                    event.getDirectoryIds(), event.getNewDirectoryId());
            log.info("目录删除知识上挂完成：kbId={}, directories={}, target={}",
                    event.getKbId(), event.getDirectoryIds(), event.getNewDirectoryId());
        } catch (Exception e) {
            log.error("目录删除知识上挂失败：kbId={}", event.getKbId(), e);
        }
    }

    /**
     * 库彻底删除：物理级联删库内全部知识（cnt_knowledge 行删除；索引清理由调用方在删除前经
     * KnowledgeApi.removeKnowledge 完成，此处幂等补一次）。
     *
     * @param event 库彻底删除事件
     */
    @EventListener
    public void onKbPurged(KbPurgedEvent event) {
        try {
            contentApi.purgeKnowledgeByKb(event.getWorkspaceId(), event.getKbId());
            log.info("知识库物理级联删除完成：kbId={}", event.getKbId());
        } catch (Exception e) {
            log.error("知识库物理级联删除失败：kbId={}", event.getKbId(), e);
        }
    }
}
