package com.calwen.xlumen.knowledge.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库可见性变更事件（F-0308，进程内事件）：可见性切换（0 私有/1 公开）落库后发布。
 * 消费方：publishing 侧监听后按维度失效公开读/检索缓存（键分片 xlumen:knowledge:{kbId}:{directoryId}，
 * 决策 D16），保证「库可见性变更即时生效」；knowledge 模块不依赖 publishing，只负责状态落库、审计与事件发布。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbVisibilityChangedEvent {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识库 ID。 */
    private Long kbId;

    /** 变更后可见性：0 私有 / 1 公开。 */
    private Integer visibility;
}
