package com.calwen.xlumen.identity.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.entity.ActivityLogEntity;
import com.calwen.xlumen.identity.mapper.ActivityLogMapper;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.identity.vo.AuditLogVO;
import com.calwen.xlumen.identity.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志服务实现（F-1202）：只增不改；跨模块由 publishing 等业务模块在关键操作后调用。
 * 时间戳由应用侧显式设置（无全局 MetaObjectHandler）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ActivityLogServiceImpl implements ActivityLogService {

    @Resource
    private ActivityLogMapper activityLogMapper;

    @Override
    public void record(Long workspaceId, Long operatorId, String operatorName, String action,
                       String targetType, Long targetId, String detailJson) {
        if (workspaceId == null || StrUtil.isBlank(action)) {
            return;
        }
        ActivityLogEntity entity = new ActivityLogEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setOperatorId(operatorId);
        entity.setOperatorName(StrUtil.nullToEmpty(operatorName));
        entity.setAction(action);
        entity.setTargetType(StrUtil.nullToEmpty(targetType));
        entity.setTargetId(targetId);
        entity.setDetailJson(detailJson);
        entity.setCreatedAt(LocalDateTime.now());
        activityLogMapper.insert(entity);
    }

    @Override
    public PageVO<AuditLogVO> listAuditLogs(String action, long pageNo, long pageSize) {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Page<ActivityLogEntity> page = activityLogMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<ActivityLogEntity>lambdaQuery()
                        .eq(ActivityLogEntity::getWorkspaceId, workspaceId)
                        .eq(StrUtil.isNotBlank(action), ActivityLogEntity::getAction, action)
                        .orderByDesc(ActivityLogEntity::getCreatedAt));
        List<AuditLogVO> records = page.getRecords().stream()
                .map(e -> AuditLogVO.builder()
                        .id(e.getId()).operatorName(e.getOperatorName()).action(e.getAction())
                        .targetType(e.getTargetType()).targetId(e.getTargetId())
                        .detailJson(e.getDetailJson()).createdAt(e.getCreatedAt()).build())
                .toList();
        return PageVO.<AuditLogVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }
}
