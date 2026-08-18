package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.ReactionStateVO;
import com.calwen.xlumen.publishing.service.LikeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识反应接口（F-0203/F-0212，B02 点赞区）：点赞/点踩三态互斥 toggle 与状态查询均需登录。
 * 用户/空间上下文由 Service 从 WorkspaceContext 读取（JWT claims），资源归属校验在服务层（双层校验第二层）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/public/knowledge/{knowledgeId}")
public class LikeController {

    @Resource
    private LikeService likeService;

    /**
     * 点赞/取消点赞（F-0203/F-0212）：三态互斥 toggle，需登录；
     * 已点踩时点赞会切换为点赞。
     *
     * @return toggle 后当前用户活动反应（LIKE|DISLIKE|NONE）
     */
    @PostMapping("/like")
    public ApiResponse<ReactionStateVO> toggleLike(@PathVariable Long knowledgeId) {
        return ApiResponse.success(
                ReactionStateVO.builder().reaction(likeService.toggleLike(knowledgeId)).build());
    }

    /**
     * 点踩/取消点踩（F-0212）：三态互斥 toggle，需登录；已点赞时点踩会切换为点踩。
     *
     * @return toggle 后当前用户活动反应（LIKE|DISLIKE|NONE）
     */
    @PostMapping("/dislike")
    public ApiResponse<ReactionStateVO> toggleDislike(@PathVariable Long knowledgeId) {
        return ApiResponse.success(
                ReactionStateVO.builder().reaction(likeService.toggleDislike(knowledgeId)).build());
    }

    /**
     * 当前用户反应状态（F-0212）：需登录，详情页初始化用（用户从 WorkspaceContext 读取）。
     */
    @GetMapping("/like/status")
    public ApiResponse<ReactionStateVO> likeStatus(@PathVariable Long knowledgeId) {
        return ApiResponse.success(
                ReactionStateVO.builder().reaction(likeService.currentReaction(knowledgeId)).build());
    }
}
