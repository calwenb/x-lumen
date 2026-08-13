package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.ReviewRequestDTO;
import com.calwen.xlumen.ai.service.ReviewService;
import com.calwen.xlumen.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 审校接口（F-0604）：提交审校任务，返回任务 ID 供轮询/SSE 订阅。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController("aiReviewController")
@RequestMapping("/api/v1/ai/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 提交审校任务（content 必填，写作与审校模型异源校验在 Service 层）。
     */
    @PostMapping
    public ApiResponse<Long> submit(@Valid @RequestBody ReviewRequestDTO dto) {
        return ApiResponse.success(reviewService.submit(dto));
    }
}
