package com.calwen.xlumen.identity.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.identity.dto.AuditLogQueryDTO;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.identity.vo.AuditLogVO;
import com.calwen.xlumen.identity.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志接口（F-1202，B05 管理后台）：只读查询，需登录访问；工作空间上下文取自可信会话（WorkspaceContext）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    @Resource
    private ActivityLogService activityLogService;

    /** 审计日志分页查询（F-1202）：action 可空（为空查全部），查询参数由 AuditLogQueryDTO 自动绑定。 */
    @GetMapping
    public ApiResponse<PageVO<AuditLogVO>> listAuditLogs(AuditLogQueryDTO query) {
        return ApiResponse.success(activityLogService.listAuditLogs(query));
    }
}
