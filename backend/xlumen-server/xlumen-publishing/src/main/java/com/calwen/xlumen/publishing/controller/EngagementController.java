package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.engagement.service.EngagementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章互动接口（F-0203，B02 评论区/点赞）：评论列表匿名可读，发表评论与点赞需登录。
 * 工作空间取自登录 JWT claims（WorkspaceContext），资源归属校验在服务层完成（双层校验第二层）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/public/articles/{articleId}")
public class EngagementController {

    @Resource
    private EngagementService engagementService;

    @Resource
    private WorkspaceApi workspaceApi;

    /**
     * 评论列表（F-0203）：按时间正序分页；匿名可读，工作空间取默认空间（MVP 单空间，决策 D9）。
     */
    @GetMapping("/comments")
    public ApiResponse<PageResult<CommentVO>> listComments(@PathVariable("articleId") Long articleId,
                                                           @RequestParam(defaultValue = "1") long pageNo,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(
                engagementService.listComments(workspaceApi.getDefaultWorkspaceId(), articleId, pageNo, pageSize));
    }

    /**
     * 发表评论（F-0203）：需登录。
     */
    @PostMapping("/comments")
    public ApiResponse<CommentVO> createComment(@PathVariable("articleId") Long articleId,
                                                @Valid @RequestBody CreateCommentDTO dto) {
        return ApiResponse.success(engagementService.createComment(WorkspaceContext.workspaceId(), articleId,
                WorkspaceContext.userId(), WorkspaceContext.username(), dto));
    }

    /**
     * 点赞/取消点赞（F-0203）：切换语义，需登录。
     *
     * @return 切换后的状态（true 已赞 / false 取消）
     */
    @PostMapping("/like")
    public ApiResponse<Boolean> toggleLike(@PathVariable("articleId") Long articleId) {
        return ApiResponse.success(
                engagementService.toggleLike(WorkspaceContext.workspaceId(), articleId, WorkspaceContext.userId()));
    }

    /**
     * 当前用户点赞状态（F-0203）：需登录，详情页初始化用。
     */
    @GetMapping("/like/status")
    public ApiResponse<Boolean> likeStatus(@PathVariable("articleId") Long articleId) {
        return ApiResponse.success(
                engagementService.isLiked(WorkspaceContext.workspaceId(), articleId, WorkspaceContext.userId()));
    }
}
