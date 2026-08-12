package com.calwen.xlumen.identity.iam.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.identity.iam.service.WorkspaceService;
import com.calwen.xlumen.identity.iam.vo.WorkspaceVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作空间接口（F-0102）：需认证访问；工作空间 ID 来自可信会话上下文（JWT claims，BACKEND.md §9）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    @Resource
    private WorkspaceService workspaceService;

    /**
     * 查询当前工作空间（含角色）：资源归属校验由 Service 完成（权限双层校验第二层）。
     *
     * @return 工作空间视图
     */
    @GetMapping("/current")
    public ApiResponse<WorkspaceVO> current() {
        return ApiResponse.success(workspaceService.getCurrent(
                WorkspaceContext.workspaceId(), WorkspaceContext.userId()));
    }
}
