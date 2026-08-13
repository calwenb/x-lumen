package com.calwen.xlumen.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌响应（F-0101）：JWT 访问令牌（短时效）+ 刷新令牌（Redis 存储哈希、GETDEL 轮换，BACKEND.md §15.3）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {

    /** 访问令牌（JWT）。 */
    private String accessToken;

    /** 刷新令牌（仅此一次明文返回，服务端只存 SHA-256 哈希）。 */
    private String refreshToken;

    /** 访问令牌有效秒数。 */
    private long expiresIn;

    /** 当前工作空间 ID（注册即建空间，决策 D9）。 */
    private Long workspaceId;

    /** 用户资料。 */
    private UserProfileVO user;
}
