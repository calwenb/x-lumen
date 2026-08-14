package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.ApproveDTO;
import com.calwen.xlumen.publishing.dto.CreateReviewDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.dto.RejectDTO;
import com.calwen.xlumen.publishing.service.ReviewService;
import com.calwen.xlumen.publishing.vo.ReviewVO;
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
 * 审核接口（F-0902/F-0903，B05 内容管理后台）：需登录访问；工作空间上下文取自可信会话（WorkspaceContext）。
 * 状态流转规则集中在 ReviewService（禁 Controller 判断状态）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController("publishingReviewController")
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    @Resource
    private ReviewService reviewService;

    /** 提交审核（F-0902）：文章进入 PENDING_REVIEW 或直接 APPROVED（强制审核关闭）。 */
    @PostMapping
    public ApiResponse<ReviewVO> submitReview(@Valid @RequestBody CreateReviewDTO dto) {
        return ApiResponse.success(reviewService.submitReview(dto.getArticleId()));
    }

    /** 审核列表（F-0902）：按状态筛选 + 分页。 */
    @GetMapping
    public ApiResponse<PageResult<ReviewVO>> listReviews(@RequestParam(value = "status", required = false) String status,
                                                         @RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                                         @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return ApiResponse.success(reviewService.listReviews(status, pageNo, pageSize));
    }

    /** 审核详情（F-0902）：越权统一 404。 */
    @GetMapping("/{id}")
    public ApiResponse<ReviewVO> getReview(@PathVariable Long id) {
        return ApiResponse.success(reviewService.getReview(id));
    }

    /** 审核通过（F-0903）：文章迁移 APPROVED(4)。 */
    @PostMapping("/{id}/approve")
    public ApiResponse<ReviewVO> approve(@PathVariable Long id, @Valid @RequestBody ApproveDTO dto) {
        return ApiResponse.success(reviewService.approve(id, dto));
    }

    /** 审核驳回（F-0903）：文章回 DRAFT(2)，写审计 REVIEW_REJECT。 */
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectDTO dto) {
        reviewService.reject(id, dto);
        return ApiResponse.success(null);
    }
}
