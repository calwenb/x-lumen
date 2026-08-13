package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.EnhanceRequestDTO;
import com.calwen.xlumen.ai.service.EnhanceService;
import com.calwen.xlumen.ai.vo.EnhanceResultVO;
import com.calwen.xlumen.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 增值接口（F-0801/F-0802）：摘要/SEO 同步生成并落库返回。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/ai/enhance")
public class EnhanceController {

    private final EnhanceService enhanceService;

    public EnhanceController(EnhanceService enhanceService) {
        this.enhanceService = enhanceService;
    }

    /**
     * 生成增值结果（SUMMARY|SEO，结构化校验后落库）。
     */
    @PostMapping
    public ApiResponse<EnhanceResultVO> enhance(@Valid @RequestBody EnhanceRequestDTO dto) {
        return ApiResponse.success(enhanceService.enhance(dto));
    }
}
