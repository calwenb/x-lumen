package com.calwen.xlumen.common.context;

/**
 * 工作空间会话作用域快照（BACKEND.md §9）：由 {@link WorkspaceContext} 承载，
 * 记录当前线程登录用户的身份与工作空间。
 *
 * @param workspaceId 当前工作空间 ID
 * @param userId      当前用户 ID
 * @param username    当前用户名（展示用，可空）
 */
public record WorkspaceScope(Long workspaceId, Long userId, String username) {
}
