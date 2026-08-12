package com.calwen.xlumen.common.web;

/**
 * 统一响应包装（BACKEND.md §10）：code/message/data/requestId。
 *
 * @param code      稳定业务码，成功为 SUCCESS，失败为具体错误码
 * @param message   用户可见的安全提示信息
 * @param data      业务数据，失败时为 null
 * @param requestId 请求追踪 ID，用于审计定位
 * @author calwen
 * @date 2026/8/12
 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    /** 成功码，与 ErrorCode.SUCCESS 保持一致。 */
    public static final String SUCCESS_CODE = "SUCCESS";

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @return 统一响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "操作成功", data, RequestId.current());
    }

    /**
     * 构造失败响应。
     *
     * @param code    稳定错误码
     * @param message 用户可见提示（不得包含内部实现细节）
     * @return 统一响应
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, RequestId.current());
    }
}
