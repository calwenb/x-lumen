package com.calwen.xlumen.publishing.engagement.service;

import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 互动服务（F-0203）：评论、点赞与统计（engagement 域，eng_ 表）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface EngagementService {

    /**
     * 分页查询文章评论（按时间正序）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     * @param pageNo      页码
     * @param pageSize    每页条数
     * @return 评论分页
     */
    PageResult<CommentVO> listComments(Long workspaceId, Long articleId, long pageNo, long pageSize);

    /**
     * 发表评论（需登录，双层校验第二层：文章必须属于当前空间）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     * @param userId      评论用户 ID
     * @param userName    评论用户名
     * @param dto         评论内容
     * @return 评论视图
     */
    CommentVO createComment(Long workspaceId, Long articleId, Long userId, String userName, CreateCommentDTO dto);

    /**
     * 点赞/取消点赞切换（唯一键幂等）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     * @param userId      用户 ID
     * @return 切换后的状态（true 已赞 / false 取消）
     */
    boolean toggleLike(Long workspaceId, Long articleId, Long userId);

    /**
     * 当前用户是否已点赞。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     * @param userId      用户 ID
     * @return 是否已赞
     */
    boolean isLiked(Long workspaceId, Long articleId, Long userId);

    /**
     * 批量评论数（IN 一次取回，避免 N+1，BACKEND.md §18）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleIds  文章 ID 列表
     * @return articleId → 评论数
     */
    Map<Long, Long> countComments(Long workspaceId, List<Long> articleIds);

    /**
     * 批量点赞数（IN 一次取回，避免 N+1）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleIds  文章 ID 列表
     * @return articleId → 点赞数
     */
    Map<Long, Long> countLikes(Long workspaceId, List<Long> articleIds);
}
