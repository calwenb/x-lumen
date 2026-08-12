package com.calwen.xlumen.common.web;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求 ID 过滤器：复用客户端 X-Request-Id 或生成新 ID，写入 MDC 并回写响应头，
 * 保证统一响应与日志中的 requestId 可审计定位（BACKEND.md §10/§15.2）。
 * 由 xlumen-boot 注册为 Bean 后生效。
 *
 * @author calwen
 * @date 2026/8/12
 */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 客户端携带的 X-Request-Id 仅作链路串联展示，服务端始终以自己的 MDC 为准
        String requestId = request.getHeader(RequestId.HEADER_NAME);
        if (StrUtil.isBlank(requestId)) {
            requestId = IdUtil.fastSimpleUUID();
        }
        RequestId.set(requestId);
        response.setHeader(RequestId.HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestId.clear();
        }
    }
}
