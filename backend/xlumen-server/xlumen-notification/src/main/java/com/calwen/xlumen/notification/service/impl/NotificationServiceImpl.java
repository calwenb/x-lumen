package com.calwen.xlumen.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.notification.entity.NotificationEntity;
import com.calwen.xlumen.notification.mapper.NotificationMapper;
import com.calwen.xlumen.notification.service.NotificationService;
import com.calwen.xlumen.notification.service.UserSseRegistry;
import com.calwen.xlumen.notification.vo.NotificationPageVO;
import com.calwen.xlumen.notification.vo.NotificationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内消息服务实现（IDEA-024）：只增不改，已读状态翻转；分页上限 100 由服务层截断（惯例同 BACKEND.md §5.1）。
 * 创建成功后经 UserSseRegistry 实时推送（前端右上角弹窗），无活跃连接时静默（30s 轮询兜底）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** 分页条数上限。 */
    private static final long PAGE_SIZE_LIMIT = 100;

    private final NotificationMapper notificationMapper;
    private final UserSseRegistry sseRegistry;

    public NotificationServiceImpl(NotificationMapper notificationMapper, UserSseRegistry sseRegistry) {
        this.notificationMapper = notificationMapper;
        this.sseRegistry = sseRegistry;
    }

    @Override
    public void create(Long workspaceId, Long userId, String type, String title, String content, String link) {
        if (userId == null) {
            return;
        }
        NotificationEntity entity = new NotificationEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title == null ? "" : title);
        entity.setContent(content == null ? "" : content);
        entity.setLink(link == null ? "" : link);
        entity.setReadFlag(0);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        notificationMapper.insert(entity);
        // 实时推送：右上角弹窗（无活跃连接时静默，轮询兜底）；推送失败不影响落库
        try {
            sseRegistry.push(userId, "notification", toVO(entity));
        } catch (Exception e) {
            log.debug("通知实时推送失败 userId={}", userId, e);
        }
    }

    @Override
    public NotificationPageVO list(Long userId, long pageNo, long pageSize) {
        requireUser(userId);
        long size = Math.min(Math.max(pageSize, 1), PAGE_SIZE_LIMIT);
        long offset = (Math.max(pageNo, 1) - 1) * size;
        Long total = notificationMapper.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId, userId));
        List<NotificationEntity> list = notificationMapper.selectList(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId, userId)
                .orderByDesc(NotificationEntity::getCreatedAt)
                .last("LIMIT " + offset + ", " + size));
        return NotificationPageVO.builder()
                .total(total == null ? 0 : total)
                .records(list.stream().map(this::toVO).toList())
                .unreadCount(unreadCount(userId))
                .build();
    }

    @Override
    public void markRead(Long userId, Long id) {
        requireUser(userId);
        NotificationEntity existing = notificationMapper.selectById(id);
        if (existing == null || !userId.equals(existing.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getId, id)
                .set(NotificationEntity::getReadFlag, 1));
    }

    @Override
    public void markAllRead(Long userId) {
        requireUser(userId);
        notificationMapper.update(null, new LambdaUpdateWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getReadFlag, 0)
                .set(NotificationEntity::getReadFlag, 1));
    }

    @Override
    public long unreadCount(Long userId) {
        requireUser(userId);
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    private NotificationVO toVO(NotificationEntity e) {
        return NotificationVO.builder()
                .id(String.valueOf(e.getId()))
                .type(e.getType())
                .title(e.getTitle())
                .content(e.getContent())
                .link(e.getLink())
                .read(Integer.valueOf(1).equals(e.getReadFlag()))
                .createdAt(e.getCreatedAt())
                .build();
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}