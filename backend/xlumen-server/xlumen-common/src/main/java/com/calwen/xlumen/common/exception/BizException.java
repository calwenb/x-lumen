package com.calwen.xlumen.common.exception;

import com.calwen.xlumen.common.web.ErrorCode;

/**
 * 业务异常：携带稳定错误码与安全提示，由全局异常处理转换为统一响应（BACKEND.md §10）。
 * 用户可见消息不得暴露内部实现与敏感数据。
 *
 * @author calwen
 * @date 2026/8/12
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode 稳定错误码
     * @param message   用户可见的安全提示
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
