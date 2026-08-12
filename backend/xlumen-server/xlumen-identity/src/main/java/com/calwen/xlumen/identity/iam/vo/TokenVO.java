package com.calwen.xlumen.identity.iam.vo;

/**
 * 令牌响应（F-0101）：JWT 访问令牌（短时效）+ 刷新令牌（Redis 存储哈希、GETDEL 轮换，BACKEND.md §15.3）。
 *
 * @param accessToken  访问令牌（JWT）
 * @param refreshToken 刷新令牌（仅此一次明文返回，服务端只存 SHA-256 哈希）
 * @param expiresIn    访问令牌有效秒数
 * @param workspaceId  当前工作空间 ID（注册即建空间，决策 D9）
 * @param user         用户资料
 * @author calwen
 * @date 2026/8/12
 */
public record TokenVO(String accessToken, String refreshToken, long expiresIn, Long workspaceId, UserProfileVO user) {
}
