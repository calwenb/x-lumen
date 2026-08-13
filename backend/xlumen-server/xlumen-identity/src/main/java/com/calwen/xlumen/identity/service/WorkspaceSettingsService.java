package com.calwen.xlumen.identity.service;

import com.calwen.xlumen.identity.dto.UpdateWorkspaceSettingsDTO;
import com.calwen.xlumen.identity.vo.WorkspaceSettingsVO;

/**
 * 工作空间设置服务（F-1201）：空间简介 + 强制审核开关（决策 D9）。
 * 工作空间上下文取自 WorkspaceContext（可信会话）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface WorkspaceSettingsService {

    /**
     * 查询当前空间设置（F-1201）。
     *
     * @return 空间设置视图
     */
    WorkspaceSettingsVO getSettings();

    /**
     * 更新当前空间设置（F-1201）：PUT 全量更新简介与强制审核开关，并写审计 WORKSPACE_SETTINGS_UPDATE。
     *
     * @param dto 设置入参
     * @return 更新后的空间设置视图
     */
    WorkspaceSettingsVO updateSettings(UpdateWorkspaceSettingsDTO dto);
}
