package com.calwen.xlumen.identity.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.identity.dto.UpdateWorkspaceSettingsDTO;
import com.calwen.xlumen.identity.service.WorkspaceSettingsService;
import com.calwen.xlumen.identity.vo.WorkspaceSettingsVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作空间设置接口（F-1201，B05 管理后台）：需登录访问；工作空间上下文取自可信会话（WorkspaceContext）。
 * 更新设置写审计 WORKSPACE_SETTINGS_UPDATE（在 Service 内完成）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/admin/workspace/settings")
public class WorkspaceSettingsController {

    @Resource
    private WorkspaceSettingsService workspaceSettingsService;

    /** 查询空间设置（F-1201）。 */
    @GetMapping
    public ApiResponse<WorkspaceSettingsVO> getSettings() {
        return ApiResponse.success(workspaceSettingsService.getSettings());
    }

    /** 更新空间设置（F-1201）：PUT 全量更新，写审计 WORKSPACE_SETTINGS_UPDATE。 */
    @PutMapping
    public ApiResponse<WorkspaceSettingsVO> updateSettings(@Valid @RequestBody UpdateWorkspaceSettingsDTO dto) {
        return ApiResponse.success(workspaceSettingsService.updateSettings(dto));
    }
}
