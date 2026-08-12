package com.calwen.xlumen.common.web;

/**
 * 稳定业务错误码（BACKEND.md §10）。HTTP 状态语义：400 参数错误、401 未登录、
 * 403 权限不足、404 资源不存在、409 冲突、429 限流/配额（字面值）、503 外部服务不可用。
 *
 * @author calwen
 * @date 2026/8/12
 */
public enum ErrorCode {

    /** 成功。 */
    SUCCESS(200, "SUCCESS", "操作成功"),
    /** 参数错误。 */
    INVALID_PARAM(400, "INVALID_PARAM", "请求参数有误"),
    /** 未登录或会话失效。 */
    UNAUTHORIZED(401, "UNAUTHORIZED", "请先登录"),
    /** 权限不足。 */
    FORBIDDEN(403, "FORBIDDEN", "无权执行该操作"),
    /** 资源不存在。 */
    NOT_FOUND(404, "NOT_FOUND", "资源不存在"),
    /** 版本、状态或幂等冲突。 */
    CONFLICT(409, "CONFLICT", "数据存在冲突，请刷新后重试"),
    /** 请求过多或配额不足（使用字面值 429，HttpServletResponse 无常量）。 */
    TOO_MANY_REQUESTS(429, "TOO_MANY_REQUESTS", "请求过于频繁，请稍后再试"),
    /** 内部错误：对外只返回 requestId，不暴露堆栈与内部信息。 */
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试"),
    /** 外部服务暂时不可用。 */
    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "依赖服务暂时不可用，请稍后重试");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
