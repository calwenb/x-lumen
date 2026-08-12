package com.calwen.xlumen.identity.iam.service;

import com.calwen.xlumen.identity.iam.dto.LoginDTO;
import com.calwen.xlumen.identity.iam.dto.RefreshTokenDTO;
import com.calwen.xlumen.identity.iam.dto.RegisterDTO;
import com.calwen.xlumen.identity.iam.vo.TokenVO;

/**
 * 认证服务（F-0101）：注册（注册即建空间）、登录、登出、令牌刷新。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface AuthService {

    /**
     * 注册：创建用户 + 默认工作空间 + OWNER 成员绑定，返回令牌。
     * 用户名冲突返回统一提示（PRODUCT §10 防枚举）；事务内完成全部写操作。
     *
     * @param dto 注册入参
     * @return 令牌响应
     */
    TokenVO register(RegisterDTO dto);

    /**
     * 登录：校验密码后签发访问令牌与刷新令牌；失败统一提示与统一延迟，不暴露账号是否存在。
     *
     * @param dto 登录入参
     * @return 令牌响应
     */
    TokenVO login(LoginDTO dto);

    /**
     * 登出：撤销刷新令牌（Redis 删除），访问令牌短时效自然过期。
     *
     * @param refreshToken 待撤销的刷新令牌
     */
    void logout(String refreshToken);

    /**
     * 刷新令牌轮换：Redis GETDEL 原子取旧值，签发新令牌对（防重放，BACKEND.md §15.3）。
     *
     * @param dto 旧刷新令牌
     * @return 新令牌响应
     */
    TokenVO refresh(RefreshTokenDTO dto);
}
