package com.calwen.xlumen.config;

import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;

/**
 * 全局异常处理：业务异常转换为稳定错误码与统一响应；未知异常只返回 requestId，
 * 不返回堆栈、SQL、密码和内部地址（BACKEND.md §10）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：按错误码映射 HTTP 状态。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * 参数校验失败：返回首个字段错误提示，统一 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.INVALID_PARAM.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM.getCode(), message));
    }

    /**
     * 路径不存在：统一 404（避免落到未知异常变 500）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getDefaultMessage()));
    }

    /**
     * 请求体不可读（JSON 解析失败）：统一 400，并透出首个解析根因（如字段类型错误），便于前端提示。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        String message = Optional.ofNullable(e.getCause())
                .map(Throwable::getMessage)
                .map(cause -> cause.split("\n")[0])
                .map(cause -> "请求参数格式错误：" + cause)
                .orElse(ErrorCode.INVALID_PARAM.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM.getCode(), message));
    }

    /**
     * 请求方法不支持（如 POST 打到 GET 端点）：统一 405（此前落 500）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM.getCode(), "请求方法不支持"));
    }

    /**
     * 路径/查询参数类型不匹配（如 id 传非数字）：统一 400（此前落 500）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM.getCode(),
                        "参数 " + e.getName() + " 类型不正确"));
    }

    /**
     * 认证失败（方法级安全入口抛出的 AuthenticationException）：统一 401。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getDefaultMessage()));
    }

    /**
     * 权限不足（F-0104 第一层：@PreAuthorize 接口权限）：统一 403。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage()));
    }

    /**
     * 未知异常：记录日志后只暴露稳定错误码与 requestId，防止内部信息泄露。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
