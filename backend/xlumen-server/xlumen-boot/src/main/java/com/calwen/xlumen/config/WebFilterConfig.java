package com.calwen.xlumen.config;

import com.calwen.xlumen.common.web.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web 装配：注册请求 ID 过滤器（BACKEND.md §10/§15.2）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Configuration
public class WebFilterConfig {

    /**
     * 请求 ID 过滤器最高优先级执行，保证后续所有日志与响应携带 requestId。
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }
}
