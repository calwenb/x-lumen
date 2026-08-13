package com.calwen.xlumen.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.identity.entity.WorkspaceEntity;
import com.calwen.xlumen.identity.mapper.WorkspaceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 工作空间对外接口实现：MVP 单空间使用（决策 D9），默认空间即第一个正常空间。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class WorkspaceApiImpl implements WorkspaceApi {

    @Resource
    private WorkspaceMapper workspaceMapper;

    @Override
    public Long getDefaultWorkspaceId() {
        WorkspaceEntity workspace = workspaceMapper.selectOne(Wrappers.<WorkspaceEntity>lambdaQuery()
                .eq(WorkspaceEntity::getStatus, 1)
                .orderByAsc(WorkspaceEntity::getId)
                .last("LIMIT 1"));
        return workspace == null ? null : workspace.getId();
    }
}
