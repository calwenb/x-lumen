package com.calwen.xlumen.identity.iam.service;

import com.calwen.xlumen.identity.iam.vo.WorkspaceVO;

/**
 * 工作空间服务（F-0102）：MVP 单空间使用（注册即建空间，决策 D9）；切换/成员邀请 V2 启用。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface WorkspaceService {

    /**
     * 查询当前用户在当前工作空间的视图（含角色）。
     *
     * @param workspaceId 工作空间 ID（来自可信会话上下文，BACKEND.md §9）
     * @param userId      当前用户 ID
     * @return 工作空间视图
     */
    WorkspaceVO getCurrent(Long workspaceId, Long userId);
}
