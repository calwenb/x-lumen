package com.calwen.xlumen.notification.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.notification.service.NotificationService;
import com.calwen.xlumen.notification.service.UserSseRegistry;
import com.calwen.xlumen.notification.vo.NotificationPageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 站内消息接口：已登录用户的消息中心（分页/已读/未读数）+ 实时推送流。
 * 默认需认证（SecurityConfig anyRequest().authenticated，BACKEND.md §9），归属校验在服务层；
 * /stream 为用户级 SSE 长连接，通知创建时推送「notification」事件（前端右上角弹窗），单向推送用 SSE 无需 WS。
 *
 * @author calwen
 * @date 2026/8/24
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserSseRegistry sseRegistry;

    public NotificationController(NotificationService notificationService, UserSseRegistry sseRegistry) {
        this.notificationService = notificationService;
        this.sseRegistry = sseRegistry;
    }

    /** 消息列表（时间倒序 + 未读数）。 */
    @GetMapping
    public ApiResponse<NotificationPageVO> list(@RequestParam(defaultValue = "1") long pageNo,
                                                @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(notificationService.list(WorkspaceContext.userId(), pageNo, pageSize));
    }

    /** 实时推送流（SSE）：新通知到达时推送 notification 事件。 */
    @GetMapping("/stream")
    public SseEmitter stream() {
        return sseRegistry.subscribe(WorkspaceContext.userId());
    }

    /** 未读数量（铃铛角标轮询）。 */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.success(notificationService.unreadCount(WorkspaceContext.userId()));
    }

    /** 标记单条已读。 */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(WorkspaceContext.userId(), id);
        return ApiResponse.success(null);
    }

    /** 全部标记已读。 */
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(WorkspaceContext.userId());
        return ApiResponse.success(null);
    }
}