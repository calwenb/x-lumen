package com.calwen.xlumen.identity.iam.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.identity.iam.dto.LoginDTO;
import com.calwen.xlumen.identity.iam.dto.RefreshTokenDTO;
import com.calwen.xlumen.identity.iam.dto.RegisterDTO;
import com.calwen.xlumen.identity.iam.service.AuthService;
import com.calwen.xlumen.identity.iam.vo.TokenVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（F-0101）：注册/登录/登出/刷新，均为公开端点（SecurityConfig 白名单）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 注册：注册成功即建空间（决策 D9）并返回令牌。
     *
     * @param dto 注册入参
     * @return 令牌响应
     */
    @PostMapping("/register")
    public ApiResponse<TokenVO> register(@Valid @RequestBody RegisterDTO dto) {
        return ApiResponse.success(authService.register(dto));
    }

    /**
     * 登录：校验通过后返回令牌；失败统一提示不暴露账号是否存在。
     *
     * @param dto 登录入参
     * @return 令牌响应
     */
    @PostMapping("/login")
    public ApiResponse<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.success(authService.login(dto));
    }

    /**
     * 刷新令牌轮换：旧令牌一次性失效（GETDEL 防重放）。
     *
     * @param dto 旧刷新令牌
     * @return 新令牌响应
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return ApiResponse.success(authService.refresh(dto));
    }

    /**
     * 登出：撤销刷新令牌。
     *
     * @param dto 刷新令牌
     * @return 统一响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenDTO dto) {
        authService.logout(dto.refreshToken());
        return ApiResponse.success(null);
    }
}
