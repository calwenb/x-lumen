package com.calwen.xlumen.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识发布成功事件（F-0402 发布即索引触发器，决策 D13）：publishing 发布成功后发布进程内 Spring 事件，
 * knowledge 模块消费并执行索引流水线。正文快照随事件携带——knowledge 不依赖 content（模块 DAG），
 * 无法反向读取正文；MVP 用进程内事件，Outbox/RocketMQ 随 F-1304（V2）升级（CHANGELOG 已记录）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePublishedEvent {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 所属知识库 ID（决策 D13 索引按库切分，KB-3 起携带）。 */
    private Long kbId;

    /** 知识 ID。 */
    private Long knowledgeId;

    /** 发布版本号（索引版本关联）。 */
    private Long version;

    /** 标题（切片元数据展示）。 */
    private String title;

    /** 正文 Markdown 快照（索引流水线取数）。 */
    private String content;
}
