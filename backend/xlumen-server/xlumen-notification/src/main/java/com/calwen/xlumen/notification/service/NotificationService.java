package com.calwen.xlumen.notification.service;

import com.calwen.xlumen.notification.vo.NotificationPageVO;

/**
 * 站内消息服务：创建/分页/已读，与具体业务事件解耦。
 *
 * @author calwen
 * @date 2026/8/24
 */
public interface NotificationService {

    /**
     * 创建站内消息。
     *
     * @param workspaceId 工作空间 ID
     * @param userId      接收用户 ID
     * @param type        事件类型（REVIEW 等）
     * @param title       标题
     * @param content     摘要内容
     * @param link        跳转链接
     */
    void create(Long workspaceId, Long userId, String type, String title, String content, String link);

    /**
     * 分页查询当前用户消息（时间倒序），附带未读数。
     *
     * @param userId   用户 ID
     * @param pageNo   页码（从 1 起）
     * @param pageSize 每页条数（≤100）
     * @return 分页结果
     */
    NotificationPageVO list(Long userId, long pageNo, long pageSize);

    /**
     * 标记单条已读（校验归属）。
     *
     * @param userId 用户 ID
     * @param id     消息 ID
     */
    void markRead(Long userId, Long id);

    /**
     * 全部标记已读。
     *
     * @param userId 用户 ID
     */
    void markAllRead(Long userId);

    /**
     * 未读数量。
     *
     * @param userId 用户 ID
     * @return 未读数
     */
    long unreadCount(Long userId);
}