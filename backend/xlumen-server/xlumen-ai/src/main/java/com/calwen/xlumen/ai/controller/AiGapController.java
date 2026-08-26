package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.service.QuestionGapService;
import com.calwen.xlumen.ai.vo.QuestionGapPageVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 问答知识缺口接口（登录）：分页查看应答无引用与建议追问，运营补库回访后置为已处理。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/ai/question-gaps")
public class AiGapController {

    private final QuestionGapService questionGapService;

    public AiGapController(QuestionGapService questionGapService) {
        this.questionGapService = questionGapService;
    }

    /** 缺口分页（工作空间内时间倒序，可按状态过滤）。 */
    @GetMapping
    public ApiResponse<QuestionGapPageVO> page(@RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") long pageNo,
                                               @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(questionGapService.page(
                WorkspaceContext.workspaceId(), status, pageNo, pageSize));
    }

    /** 置为已处理（归属校验工作空间一致）。 */
    @PostMapping("/{id}/handled")
    public ApiResponse<Void> markHandled(@PathVariable Long id) {
        questionGapService.markHandled(WorkspaceContext.workspaceId(), id);
        return ApiResponse.success(null);
    }
}