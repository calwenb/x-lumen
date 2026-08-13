package com.calwen.xlumen.identity.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.identity.vo.AuditLogVO;
import com.calwen.xlumen.identity.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 审计日志分页查询（F-1202）：action 可空（为空查全部）。 */
    @GetMapping
    public ApiResponse<PageVO<AuditLogVO>> listAuditLogs(@RequestParam(value = "action", required = false) String action,
                                                         @RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                                                         @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return ApiResponse.success(activityLogService.listAuditLogs(action, pageNo, pageSize));
    }
}
