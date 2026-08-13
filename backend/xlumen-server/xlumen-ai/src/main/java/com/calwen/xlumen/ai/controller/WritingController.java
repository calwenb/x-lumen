package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.WritingRequestDTO;
import com.calwen.xlumen.ai.service.WritingService;
import com.calwen.xlumen.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 写作接口（F-0601）：提交写作任务，返回任务 ID 供轮询/SSE 订阅。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/ai/writing")
public class WritingController {

    private final WritingService writingService;

    public WritingController(WritingService writingService) {
        this.writingService = writingService;
    }

    /**
     * 提交写作任务（topic/draft/content 至少一项，Service 层校验）。
     */
    @PostMapping
    public ApiResponse<Long> submit(@Valid @RequestBody WritingRequestDTO dto) {
        return ApiResponse.success(writingService.submit(dto));
    }
}
