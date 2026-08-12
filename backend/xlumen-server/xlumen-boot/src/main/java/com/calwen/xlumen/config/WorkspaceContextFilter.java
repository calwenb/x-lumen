package com.calwen.xlumen.config;

import com.calwen.xlumen.common.context.WorkspaceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 工作空间上下文过滤器（F-0102/F-0104，BACKEND.md §9）：认证成功后从 JWT claims
 * 建立 WorkspaceContext（workspaceId 只来自可信会话上下文，不信任 URL/Header/DTO）。
 * 在 Security 授权过滤器之后执行；匿名请求不设置上下文。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Component
public class WorkspaceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                // set(workspaceId, userId, username)：全部来自可信 JWT claims
                WorkspaceContext.set(jwt.getClaim("workspaceId"), Long.valueOf(jwt.getSubject()),
                        jwt.getClaimAsString("username"));
            }
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束清理上下文，防止线程复用串号
            WorkspaceContext.clear();
        }
    }
}
