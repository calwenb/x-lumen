package com.calwen.xlumen.publishing.service;

import java.util.List;
import java.util.Map;

/**
 * 点赞服务（F-0203）：切换/状态与批量统计（eng_like 表）。
 * 登录态接口的用户/空间上下文从 WorkspaceContext 读取。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface LikeService {

    /**
     * 点赞/取消点赞切换（需登录；唯一键幂等）。
     *
     * @param knowledgeId 知识 ID
     * @return 切换后的状态（true 已赞 / false 取消）
     */
    boolean toggleLike(Long knowledgeId);

    /**
     * 当前用户是否已点赞（用户从 WorkspaceContext 读取，匿名为 false；详情页初始化用）。
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
     * @param workspaceId 工作空间 ID
     * @param knowledgeIds  知识 ID 列表
     * @return knowledgeId → 点赞数
     */
    Map<Long, Long> countLikes(Long workspaceId, List<Long> knowledgeIds);
}
