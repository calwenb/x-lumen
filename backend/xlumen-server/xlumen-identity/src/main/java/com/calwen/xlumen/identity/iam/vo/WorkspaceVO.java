package com.calwen.xlumen.identity.iam.vo;

/**
 * 工作空间视图（F-0102）：MVP 注册即建空间单空间使用（决策 D9），切换与成员邀请 V2 启用。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record WorkspaceVO(Long workspaceId, String name, String slug, String roleCode) {
}
