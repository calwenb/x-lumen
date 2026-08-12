package com.calwen.xlumen.identity.iam.service;

/**
 * 刷新令牌服务（F-0101）：短期状态存 Redis（决策 D6），
 * 不存明文只存 SHA-256 哈希，轮换用 GETDEL 原子操作防重放（BACKEND.md §15.3）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface RefreshTokenService {

    /** 刷新令牌有效期（秒）：7 天，短时效且支持撤销。 */
    long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 3600L;

    /**
     * 创建刷新令牌：返回明文（仅此一次），服务端只存哈希。
     *
     * @param userId      用户 ID
     * @param workspaceId 工作空间 ID
     * @return 刷新令牌明文
     */
    String create(Long userId, Long workspaceId);

    /**
     * 校验并轮换刷新令牌：GETDEL 原子取旧值，成功则签发新令牌（防重放）。
     *
     * @param refreshToken 旧刷新令牌明文
     * @return 解析结果（userId/workspaceId）；令牌无效返回 null
     */
    RefreshSession rotate(String refreshToken);

    /**
     * 撤销刷新令牌（登出）。
     *
     * @param refreshToken 刷新令牌明文
     */
    void revoke(String refreshToken);

    /**
     * 刷新会话快照。
     *
     * @param userId      用户 ID
     * @param workspaceId 工作空间 ID
     */
    record RefreshSession(Long userId, Long workspaceId) {
    }
}
