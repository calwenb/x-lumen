package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.publishing.dto.ReactionStateVO;

import java.util.List;
import java.util.Map;

/**
 * 知识反应服务（F-0203/F-0212）：三态互斥 toggle（赞/踩共用一行）、状态查询与批量统计（eng_like 表）。
 * 登录态接口的用户/空间上下文从 WorkspaceContext 读取。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface LikeService {

    /**
     * 点赞 toggle（需登录；三态互斥，唯一键幂等）。
     * 语义：无活动反应->点赞；已点赞->取消；已点踩->切换为点赞。
     *
     * @param knowledgeId 知识 ID
     * @return toggle 后活动反应：LIKE|DISLIKE|NONE（ReactionStateVO 常量）
     */
    String toggleLike(Long knowledgeId);

    /**
     * 点踩 toggle（需登录；三态互斥，唯一键幂等）。
     * 语义：无活动反应->点踩；已点踩->取消；已点赞->切换为点踩。
     *
     * @param knowledgeId 知识 ID
     * @return toggle 后活动反应：LIKE|DISLIKE|NONE（ReactionStateVO 常量）
     */
    String toggleDislike(Long knowledgeId);

    /**
     * 当前用户对知识的活动反应（用户从 WorkspaceContext 读取，匿名为 NONE；详情页初始化用）。
     *
     * @param knowledgeId 知识 ID
     * @return LIKE|DISLIKE|NONE（ReactionStateVO 常量）
     */
    String currentReaction(Long knowledgeId);

    /**
     * 当前用户是否已点赞（用户从 WorkspaceContext 读取，匿名为 false）。
     *
     * @param knowledgeId 知识 ID
     * @return 是否已赞
     */
    boolean isLiked(Long knowledgeId);

    /**
     * 当前用户是否已点赞（指定上下文，供公开读聚合内部调用）。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @param userId      用户 ID
     * @return 是否已赞
     */
    boolean isLiked(Long workspaceId, Long knowledgeId, Long userId);

    /**
     * 批量点赞数（IN 一次取回，避免 N+1；供公开读聚合）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间聚合）
     * @param knowledgeIds  知识 ID 列表
     * @return knowledgeId -> 点赞数
     */
    Map<Long, Long> countLikes(Long workspaceId, List<Long> knowledgeIds);

    /**
     * 批量点踩数（IN 一次取回，避免 N+1；供公开读聚合）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间聚合）
     * @param knowledgeIds  知识 ID 列表
     * @return knowledgeId -> 点踩数
     */
    Map<Long, Long> countDislikes(Long workspaceId, List<Long> knowledgeIds);
}
