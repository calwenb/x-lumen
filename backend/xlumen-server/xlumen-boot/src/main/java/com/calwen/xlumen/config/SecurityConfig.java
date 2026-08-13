package com.calwen.xlumen.config;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 安全装配（F-0101/F-0104，BACKEND.md §2/§9）：OAuth2 resource-server（JWT 认证）、无状态会话；
 * 401/403 统一 JSON 响应；方法级安全用于接口权限（双层校验第一层）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 安全过滤链：/api/v1/auth/**（注册/登录/刷新/登出）与可观测端点公开，其余 API 需认证。
     *
     * @param http HttpSecurity
     * @return 过滤链
     * @throws Exception 装配异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, WorkspaceContextFilter workspaceContextFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/system/**", "/actuator/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 博客前台公开读（F-0201~F-0203，B01~B04）：GET 全部匿名；阅读量上报匿名；读者纠错匿名（F-1001）；评论/点赞需登录
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/articles/*/view").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/articles/*/feedback").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN)))
                // 授权完成后建立工作空间上下文（权限变化即时生效，BACKEND.md §9）
                .addFilterAfter(workspaceContextFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }

    /**
     * JWT claims → GrantedAuthority 转换：roles 列表映射为 ROLE_xxx，供方法级安全使用。
     *
     * @return 认证转换器
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return authorities;
        });
        return converter;
    }

    /** 密码哈希（F-0101）：BCrypt 存储（BACKEND.md §15.3）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 401/403 统一 JSON 响应，与 ApiResponse 结构一致（BACKEND.md §10）。 */
    private void writeError(HttpServletResponse response, int status, ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(errorCode.getCode(), errorCode.getDefaultMessage())));
    }
}
