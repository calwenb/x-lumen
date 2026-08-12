package com.calwen.xlumen.identity.iam.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参（F-0101）：登录失败统一提示与统一延迟（PRODUCT §10 防枚举）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record LoginDTO(

        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
