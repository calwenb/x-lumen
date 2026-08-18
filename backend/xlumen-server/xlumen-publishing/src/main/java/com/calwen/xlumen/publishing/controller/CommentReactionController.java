package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.ReactionStateVO;
import com.calwen.xlumen.publishing.service.CommentReactionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论反应接口（F-0213，B02 评论区）：点赞/点踩三态互斥 toggle 均需登录。
 * 用户/空间上下文由 Service 从 WorkspaceContext 读取（JWT claims），评论存在性校验在服务层。
 *
 * @author calwen
 * @date 2026/8/18
 */
@RestController
@RequestMapping("/api/v1/public/comments/{commentId}")
public class CommentReactionController {

    @Resource
    private CommentReactionService commentReactionService;

    /**
     * 评论点赞/取消（F-0213）：三态互斥 toggle，需登录；已点踩时点赞会切换为点赞。
     *
     * @return toggle 后当前用户活动反应（LIKE|DISLIKE|NONE）
     */
    @PostMapping("/like")
    public ApiResponse<ReactionStateVO> toggleLike(@PathVariable Long commentId) {
        return ApiResponse.success(
                ReactionStateVO.builder().reaction(commentReactionService.toggleLike(commentId)).build());
    }

    /**
     * 评论点踩/取消（F-0213）：三态互斥 toggle，需登录；已点赞时点踩会切换为点踩。
     *
     * @return toggle 后当前用户活动反应（LIKE|DISLIKE|NONE）
     */
    @PostMapping("/dislike")
    public ApiResponse<ReactionStateVO> toggleDislike(@PathVariable Long commentId) {
        return ApiResponse.success(
                ReactionStateVO.builder().reaction(commentReactionService.toggleDislike(commentId)).build());
    }
}
