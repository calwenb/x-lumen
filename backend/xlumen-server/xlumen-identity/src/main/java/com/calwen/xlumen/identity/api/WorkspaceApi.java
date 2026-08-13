package com.calwen.xlumen.identity.api;

/**
 * 身份模块对外接口（BACKEND.md §5.2）：工作空间能力供其他模块编排。
 * MVP 单空间使用（决策 D9），公开读接口依赖默认空间 ID。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface WorkspaceApi {

    /**
     * 查询默认公开工作空间 ID（个人博客 MVP 单空间：第一个正常空间）。
     *
     * @return 工作空间 ID；无可用空间返回 null
     */
    Long getDefaultWorkspaceId();

    /**
     * 查询工作空间强制审核开关（F-1201，决策 D9）：用于审核流程判断是否跳过 AI 审校直接通过。
     *
     * @param workspaceId 工作空间 ID
     * @return true 强制审核开启；false 关闭；空间不存在或未配置时默认 true（保守开启）
     */
    Boolean forceReviewEnabled(Long workspaceId);
}
