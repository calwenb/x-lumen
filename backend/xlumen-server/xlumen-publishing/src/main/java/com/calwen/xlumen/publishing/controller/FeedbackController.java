package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.CreateFeedbackDTO;
import com.calwen.xlumen.publishing.service.FeedbackService;
import com.calwen.xlumen.publishing.vo.FeedbackVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读者纠错接口（F-1001，B02 评论区）：匿名可提交（SecurityConfig 已放行 /api/v1/public/**）；
 * 提交者 IP 由服务端从请求回填（用于限流，不信任请求体），工作空间取默认空间。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/public/articles/{articleId}/feedback")
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    /** 提交读者纠错（F-1001）：匿名可提交，返回追踪号。 */
    @PostMapping
    public ApiResponse<FeedbackVO> createFeedback(@PathVariable Long articleId,
                                                  @Valid @RequestBody CreateFeedbackDTO dto,
                                                  HttpServletRequest request) {
        dto.setIp(clientIp(request));
        return ApiResponse.success(feedbackService.createFeedback(articleId, dto));
    }

    /** 客户端 IP：优先取反向代理传递的 X-Forwarded-For，否则取 RemoteAddr。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
