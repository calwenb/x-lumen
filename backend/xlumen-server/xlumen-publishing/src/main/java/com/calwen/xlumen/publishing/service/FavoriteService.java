package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 知识收藏服务（F-0212）：收藏 toggle、状态与批量统计、我的收藏分页（eng_favorite 表）。
 * 登录态接口的用户/空间上下文从 WorkspaceContext 读取。
 *
 * @author calwen
 * @date 2026/8/18
 */
public interface FavoriteService {

    /**
     * 收藏/取消收藏 toggle（需登录；唯一键幂等）。
     *
     * @param knowledgeId 知识 ID
     * @return toggle 后状态（true 已收藏 / false 取消）
     */
    boolean toggleFavorite(Long knowledgeId);

    /**
     * 指定用户是否已收藏（供公开读聚合内部调用）。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId 知识 ID
     * @param userId      用户 ID
     * @return 是否已收藏
     */
    boolean isFavorited(Long workspaceId, Long knowledgeId, Long userId);

    /**
     * 批量收藏数（IN 一次取回，避免 N+1；供公开读聚合）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间聚合）
     * @param knowledgeIds 知识 ID 列表
     * @return knowledgeId -> 收藏数
     */
    Map<Long, Long> countFavorites(Long workspaceId, List<Long> knowledgeIds);

    /**
     * 我的收藏分页（需登录）：按收藏时间倒序，逐条做公开可见性过滤
     * （公开库已发布，或库主自己的私有库已发布）。
     *
     * @param query 分页参数
     * @return 收藏知识卡片分页（卡片额外携带 favoritedAt）
     */
    PageResult<KnowledgeCardVO> listFavorites(PageQueryDTO query);
}
