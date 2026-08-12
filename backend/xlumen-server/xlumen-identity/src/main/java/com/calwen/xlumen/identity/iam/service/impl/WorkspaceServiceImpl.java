package com.calwen.xlumen.identity.iam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.iam.entity.WorkspaceEntity;
import com.calwen.xlumen.identity.iam.entity.WorkspaceMemberEntity;
import com.calwen.xlumen.identity.iam.mapper.WorkspaceMapper;
import com.calwen.xlumen.identity.iam.mapper.WorkspaceMemberMapper;
import com.calwen.xlumen.identity.iam.service.WorkspaceService;
import com.calwen.xlumen.identity.iam.vo.WorkspaceVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 工作空间服务实现（F-0102）：MVP 单空间使用；资源归属校验（权限双层校验第二层，BACKEND.md §9）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Resource
    private WorkspaceMapper workspaceMapper;

    @Resource
    private WorkspaceMemberMapper memberMapper;

    @Override
    public WorkspaceVO getCurrent(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 第二层校验：资源归属与数据范围（BACKEND.md §9），防止越权访问其他空间
        WorkspaceMemberEntity member = memberMapper.selectOne(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceMemberEntity::getUserId, userId)
                .eq(WorkspaceMemberEntity::getStatus, 1));
        if (member == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该工作空间");
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || workspace.getStatus() == null || workspace.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作空间不存在或已停用");
        }
        return new WorkspaceVO(workspace.getId(), workspace.getName(), workspace.getSlug(), member.getRoleCode());
    }
}
