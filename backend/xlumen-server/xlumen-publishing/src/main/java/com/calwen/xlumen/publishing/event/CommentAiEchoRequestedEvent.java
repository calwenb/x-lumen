package com.calwen.xlumen.publishing.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论 AI 回声请求事件（进程内 Spring 事件，@EventListener 同步消费）：
 * 评论正文命中 @小光 时由发表链路发布，监听方检索知识片段生成小光回复
 * （V2 先走检索摘要，模型生成留待提示词管配后升级）；回复生成失败不影响评论主流程。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentAiEchoRequestedEvent {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识 ID（回复评论所属知识）。 */
    private Long knowledgeId;

    /** 触发用户 ID（@小光 的评论者，检索可见库推导与限流键用）。 */
    private Long userId;

    /** 知识标题（知识详情快照，可空）。 */
    private String knowledgeTitle;

    /** 知识正文 Markdown（知识详情快照，可空；V2 模型生成备用）。 */
    private String knowledgeContent;

    /** 触发评论 ID（小光回复的 parent_id）。 */
    private Long commentId;

    /** 触发评论内容（检索 query）。 */
    private String commentContent;

    /** 触发评论用户名（冗余展示字段）。 */
    private String username;
}