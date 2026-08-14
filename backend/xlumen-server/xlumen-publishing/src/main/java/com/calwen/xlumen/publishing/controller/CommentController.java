package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.CommentQueryDTO;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.CommentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章评论接口（F-0203，B02 评论区）：评论列表匿名可读，发表评论需登录。
 * 用户/空间上下文由 Service 从 WorkspaceContext 读取（JWT claims），资源归属校验在服务层（双层校验第二层）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/public/articles/{articleId}/comments")
public class CommentController {

    @Resource
    private CommentService commentService;

    /**
     * 评论列表（F-0203）：按时间正序分页；匿名可读，空间取默认空间。
     */
    @GetMapping
    public ApiResponse<PageResult<CommentVO>> listComments(@PathVariable Long articleId,
                                                           CommentQueryDTO query) {
        return ApiResponse.success(commentService.listComments(articleId, query));
    }

    /**
     * 发表评论（F-0203）：需登录。
     */
    @PostMapping
    public ApiResponse<CommentVO> createComment(@PathVariable Long articleId,
                                                @Valid @RequestBody CreateCommentDTO dto) {
        return ApiResponse.success(commentService.createComment(articleId, dto));
    }
}
