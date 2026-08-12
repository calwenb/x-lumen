package com.calwen.xlumen.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统探活接口：骨架阶段用于验证统一响应包装与请求 ID 链路（健康检查另见 /actuator/health）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 探活：返回 pong 与当前 requestId。
     *
     * @return 统一响应
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }
}
