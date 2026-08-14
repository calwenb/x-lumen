package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.service.LikeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识点赞接口（F-0203，B02 点赞区）：点赞切换与状态查询均需登录。
 * 用户/空间上下文由 Service 从 WorkspaceContext 读取（JWT claims），资源归属校验在服务层（双层校验第二层）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/public/knowledge/{knowledgeId}/like")
public class LikeController {

    @Resource
    private LikeService likeService;

    /**
     * 点赞/取消点赞（F-0203）：切换语义，需登录。
     *
     * @return 切换后的状态（true 已赞 / false 取消）
     */
    @PostMapping
    public ApiResponse<Boolean> toggleLike(@PathVariable Long knowledgeId) {
        return ApiResponse.success(likeService.toggleLike(knowledgeId));
    }

    /**
     * 当前用户点赞状态（F-0203）：需登录，详情页初始化用（用户从 WorkspaceContext 读取）。
     */
    @GetMapping("/status")
    public ApiResponse<Boolean> likeStatus(@PathVariable Long knowledgeId) {
        return ApiResponse.success(likeService.isLiked(knowledgeId));
    }
}
