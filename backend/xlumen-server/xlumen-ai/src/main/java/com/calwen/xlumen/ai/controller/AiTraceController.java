package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.service.AiCallLogService;
import com.calwen.xlumen.ai.vo.TracePageVO;
import com.calwen.xlumen.ai.vo.TraceSummaryVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用追踪接口（管理面）：分页查询 + 今日用量统计，仅 OWNER 可见。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/admin/ai-traces")
@PreAuthorize("hasRole('OWNER')")
public class AiTraceController {

    private final AiCallLogService aiCallLogService;

    public AiTraceController(AiCallLogService aiCallLogService) {
        this.aiCallLogService = aiCallLogService;
    }

    /** 调用追踪分页（时间倒序，可按场景/成败过滤）。 */
    @GetMapping
    public ApiResponse<TracePageVO> page(@RequestParam(required = false) String scene,
                                         @RequestParam(required = false) Boolean success,
                                         @RequestParam(defaultValue = "1") long pageNo,
                                         @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(aiCallLogService.page(
                WorkspaceContext.workspaceId(), scene, success, pageNo, pageSize));
    }

    /** 今日用量统计：总数/失败数/按场景分布。 */
    @GetMapping("/summary")
    public ApiResponse<TraceSummaryVO> summary() {
        return ApiResponse.success(aiCallLogService.summary(WorkspaceContext.workspaceId()));
    }
}