package com.calwen.xlumen.identity.service;

/**
 * 刷新会话快照（F-0101）：{@link RefreshTokenService#rotate} 轮换成功后返回的用户身份。
 *
 * @param userId      用户 ID
 * @param workspaceId 工作空间 ID
 */
public record RefreshSession(Long userId, Long workspaceId) {
}
