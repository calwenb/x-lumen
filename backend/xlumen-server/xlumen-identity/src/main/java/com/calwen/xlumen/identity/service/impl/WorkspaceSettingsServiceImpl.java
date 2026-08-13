package com.calwen.xlumen.identity.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.dto.UpdateWorkspaceSettingsDTO;
import com.calwen.xlumen.identity.entity.WorkspaceEntity;
import com.calwen.xlumen.identity.mapper.WorkspaceMapper;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.identity.service.WorkspaceSettingsService;
import com.calwen.xlumen.identity.vo.WorkspaceSettingsVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作空间设置服务实现（F-1201）：简介 + 强制审核开关（决策 D9）；更新后写审计 WORKSPACE_SETTINGS_UPDATE。
 * 设置内容校验/权限在 Controller 入口（登录态）+ 本服务（第二层：空间存在性）完成。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class WorkspaceSettingsServiceImpl implements WorkspaceSettingsService {

    private static final JsonMapper JSON = new JsonMapper();

    @Resource
    private WorkspaceMapper workspaceMapper;

    @Resource
    private ActivityLogService activityLogService;

    @Override
    public WorkspaceSettingsVO getSettings() {
        return toVO(getOwnedWorkspace());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceSettingsVO updateSettings(UpdateWorkspaceSettingsDTO dto) {
        WorkspaceEntity workspace = getOwnedWorkspace();
        workspace.setIntro(StrUtil.nullToEmpty(dto.getIntro()));
        workspace.setForceReview(Boolean.TRUE.equals(dto.getForceReview()) ? 1 : 0);
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.updateById(workspace);

        activityLogService.record(workspace.getId(), WorkspaceContext.userId(), WorkspaceContext.username(),
                "WORKSPACE_SETTINGS_UPDATE", "WORKSPACE", workspace.getId(),
                buildDetailJson(dto.getIntro(), dto.getForceReview()));
        return toVO(workspace);
    }

    /** 当前空间：登录态校验 + 空间存在性（双层校验第二层，BACKEND.md §9）。 */
    private WorkspaceEntity getOwnedWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || workspace.getStatus() == null || workspace.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作空间不存在或已停用");
        }
        return workspace;
    }

    /** 审计详情：{"intro","forceReview"}（forceReview 记录为布尔语义，供审计还原）。 */
    private String buildDetailJson(String intro, Boolean forceReview) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("intro", StrUtil.nullToEmpty(intro));
        detail.put("forceReview", Boolean.TRUE.equals(forceReview));
        return JSON.writeValueAsString(detail);
    }

    private WorkspaceSettingsVO toVO(WorkspaceEntity w) {
        return WorkspaceSettingsVO.builder()
                .workspaceId(w.getId()).name(w.getName()).slug(w.getSlug())
                .intro(w.getIntro())
                .forceReview(w.getForceReview() == null || w.getForceReview() == 1)
                .build();
    }
}
