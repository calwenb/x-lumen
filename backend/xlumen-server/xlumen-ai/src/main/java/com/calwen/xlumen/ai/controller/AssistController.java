package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.AssistDTO;
import com.calwen.xlumen.ai.service.AssistService;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 交互式辅助接口（辅助编辑/代码解读）：需登录，场景 WRITING 纳入配额与调用追踪。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/ai/assist")
public class AssistController {

    private final AssistService assistService;

    public AssistController(AssistService assistService) {
        this.assistService = assistService;
    }

    /** 执行辅助能力，返回生成的文本。 */
    @PostMapping
    public ApiResponse<Map<String, String>> assist(@Valid @RequestBody AssistDTO dto) {
        try {
            String text = assistService.assist(WorkspaceContext.workspaceId(), dto);
            return ApiResponse.success(Map.of("text", text));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INVALID_PARAM, e.getMessage());
        }
    }
}