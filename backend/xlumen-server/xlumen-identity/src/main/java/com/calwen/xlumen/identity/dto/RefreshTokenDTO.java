package com.calwen.xlumen.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 令牌刷新入参（F-0101）：刷新令牌短时效且支持撤销（BACKEND.md §15.3，Redis GETDEL 轮换防重放）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record RefreshTokenDTO(

        @NotBlank(message = "刷新令牌不能为空")
        String refreshToken
) {
}
