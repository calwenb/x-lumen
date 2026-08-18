package com.calwen.xlumen.publishing.service;

import java.util.List;
import java.util.Map;

/**
 * 评论反应服务（F-0213）：三态互斥 toggle（赞/踩共用一行）与批量统计（eng_comment_reaction 表）。
 * 登录态接口的用户/空间上下文从 WorkspaceContext 读取。
 *
 * @author calwen
 * @date 2026/8/18
 */
public interface CommentReactionService {

    /**
     * 评论点赞 toggle（需登录；三态互斥，唯一键幂等；评论需存在且正常）。
     * 语义：无活动反应->点赞；已点赞->取消；已点踩->切换为点赞。
     *
     * @param commentId 评论 ID
     * @return toggle 后活动反应：LIKE|DISLIKE|NONE（ReactionStateVO 常量）
     */
    String toggleLike(Long commentId);

    /**
     * 评论点踩 toggle（需登录；三态互斥，唯一键幂等；评论需存在且正常）。
     * 语义：无活动反应->点踩；已点踩->取消；已点赞->切换为点踩。
     *
     * @param commentId 评论 ID
     * @return toggle 后活动反应：LIKE|DISLIKE|NONE（ReactionStateVO 常量）
     */
    String toggleDislike(Long commentId);

    /**
     * 批量评论点赞数（IN 一次取回，避免 N+1；供评论列表聚合）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间聚合）
     * @param commentIds  评论 ID 列表
     * @return commentId -> 点赞数
     */
    Map<Long, Long> countLikes(Long workspaceId, List<Long> commentIds);

    /**
     * 批量评论点踩数（IN 一次取回，避免 N+1；供评论列表聚合）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间聚合）
     * @param commentIds  评论 ID 列表
     * @return commentId -> 点踩数
     */
    Map<Long, Long> countDislikes(Long workspaceId, List<Long> commentIds);

    /**
     * 批量当前用户反应（IN 一次取回，避免 N+1；匿名为空 Map，列表 myReaction 置 null）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间聚合）
     * @param commentIds  评论 ID 列表
     * @param userId      用户 ID（可空=匿名）
     * @return commentId -> LIKE|DISLIKE（仅活动反应；无活动反应的评论不在 Map 中）
     */
    Map<Long, String> mapUserReactions(Long workspaceId, List<Long> commentIds, Long userId);
}
