package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.TermExplainDTO;
import com.calwen.xlumen.ai.service.impl.TermExplainService;
import com.calwen.xlumen.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 术语解释接口（访客可用）：详情页选词 → AI 1~2 句解释，24 小时短缓存。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/ai/term-explain")
public class TermExplainController {

    private final TermExplainService termExplainService;

    public TermExplainController(TermExplainService termExplainService) {
        this.termExplainService = termExplainService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> explain(@Valid @RequestBody TermExplainDTO dto) {
        TermExplainService.TermExplainResult result = termExplainService.explain(dto.getTerm());
        return ApiResponse.success(Map.of(
                "term", result.term(),
                "explanation", result.explanation(),
                "fromCache", result.fromCache()));
    }
}