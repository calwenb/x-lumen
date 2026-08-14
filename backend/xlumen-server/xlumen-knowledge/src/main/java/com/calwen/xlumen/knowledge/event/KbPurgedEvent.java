package com.calwen.xlumen.knowledge.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库彻底删除事件（F-0305，进程内事件）：回收站「彻底删除」知识库（物理删除 kb_knowledge_base 行）后发布。
 * 消费方：content 模块（KB-3 content 改造）监听后物理删除库内知识（cnt_knowledge，方案 §7.2 物理级联删知识）
 * 并逐条触发索引清理（KnowledgeApi.removeKnowledge）；publishing 侧据此失效公开读缓存。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbPurgedEvent {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识库 ID。 */
    private Long kbId;
}
