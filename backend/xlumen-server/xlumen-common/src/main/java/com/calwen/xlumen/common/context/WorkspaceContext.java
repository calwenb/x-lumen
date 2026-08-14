package com.calwen.xlumen.common.context;

/**
 * 工作空间上下文：登录成功后建立用户与当前工作空间上下文（BACKEND.md §9）。
 * 工作空间 ID 只来自可信会话上下文，不信任 URL/Header/DTO 中的值；
 * 所有工作空间业务查询必须包含 workspace_id 条件（多租户双层校验第二层依赖此上下文）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public final class WorkspaceContext {

    private static final ThreadLocal<WorkspaceScope> HOLDER = new ThreadLocal<>();

    private WorkspaceContext() {
    }

    /**
     * 建立当前线程的工作空间上下文（认证过滤器/拦截器在会话校验后调用）。
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     */
    public static void set(Long workspaceId, Long userId) {
        HOLDER.set(new WorkspaceScope(workspaceId, userId, null));
    }

    /**
     * 建立当前线程的工作空间上下文（含用户名，供评论等展示场景使用）。
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID
     * @param username    用户名
     */
    public static void set(Long workspaceId, Long userId, String username) {
        HOLDER.set(new WorkspaceScope(workspaceId, userId, username));
    }

    /**
     * 获取当前工作空间 ID；未登录或未选择空间时返回 null，由调用方按 401/403 处理。
     *
     * @return 工作空间 ID 或 null
     */
    public static Long workspaceId() {
        WorkspaceScope scope = HOLDER.get();
        return scope == null ? null : scope.workspaceId();
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 用户 ID 或 null
     */
    public static Long userId() {
        WorkspaceScope scope = HOLDER.get();
        return scope == null ? null : scope.userId();
    }

    /**
     * 获取当前用户名（展示用）。
     *
     * @return 用户名或 null
     */
    public static String username() {
        WorkspaceScope scope = HOLDER.get();
        return scope == null ? null : scope.username();
    }

    /** 清理上下文（请求结束时调用，防止线程复用串号）。 */
    public static void clear() {
        HOLDER.remove();
    }
}
