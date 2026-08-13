package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.publishing.dto.CommentQueryDTO;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 评论服务（F-0203）：列表/发表与批量统计（eng_comment 表）。
 * 登录态接口的用户/空间上下文从 WorkspaceContext 读取；匿名评论列表取默认空间。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface CommentService {

    /**
     * 分页查询文章评论（按时间正序；匿名可读，空间取默认空间）。
     *
     * @param articleId 文章 ID
     * @param query     分页参数
     * @return 评论分页
     */
    PageResult<CommentVO> listComments(Long articleId, CommentQueryDTO query);

    /**
     * 发表评论（需登录；用户/空间从 WorkspaceContext 读取，双层校验第二层：文章必须属于当前空间）。
     *
     * @param articleId 文章 ID
     * @param dto       评论内容
     * @return 评论视图
     */
    CommentVO createComment(Long articleId, CreateCommentDTO dto);

    /**
     * 批量评论数（IN 一次取回，避免 N+1，BACKEND.md §18；供公开读聚合）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleIds  文章 ID 列表
     * @return articleId → 评论数
     */
    Map<Long, Long> countComments(Long workspaceId, List<Long> articleIds);
}
