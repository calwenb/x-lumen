package com.calwen.xlumen.common.web;

import cn.hutool.core.util.IdUtil;
import org.slf4j.MDC;

/**
 * 请求 ID 上下文：为每个请求生成稳定追踪 ID，贯穿日志、统一响应与审计（BACKEND.md §10/§15.2）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public final class RequestId {

    /** MDC 键名，logback 模式串以 %X{requestId} 引用。 */
    public static final String MDC_KEY = "requestId";

    /** 请求头名称：客户端可携带 X-Request-Id 串联前后端链路（FRONTEND.md §8.1）。 */
    public static final String HEADER_NAME = "X-Request-Id";

    private RequestId() {
    }

    /**
     * 获取当前请求 ID；不存在时生成新的（异步线程需先由调用方透传）。
     *
     * @return 当前请求 ID
     */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        if (value == null || value.isEmpty()) {
            value = IdUtil.fastSimpleUUID();
            MDC.put(MDC_KEY, value);
        }
        return value;
    }

    /**
     * 设置当前请求 ID（过滤器入口调用）。
     *
     * @param requestId 请求 ID
     */
    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    /** 清理当前请求 ID（请求结束时调用，防止线程复用串号）。 */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
