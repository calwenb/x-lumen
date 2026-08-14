package com.calwen.xlumen.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.knowledge.entity.KbKnowledgeBaseEntity;
import com.calwen.xlumen.knowledge.mapper.KbKnowledgeBaseMapper;
import com.calwen.xlumen.knowledge.service.VisibilityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 可见库集合推导实现（F-0407 单一实现，决策 D13/D16）：只读 kb_knowledge_base 推导身份可见集合，
 * 供 publishing 公开读聚合、检索过滤与知识列表共用；V2 授权库（kb_kb_grant）不实现。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Service
public class VisibilityServiceImpl implements VisibilityService {

    @Resource
    private KbKnowledgeBaseMapper kbMapper;
    @Resource
    private WorkspaceApi workspaceApi;

    @Override
    public List<Long> resolveVisibleKbIds(Long userId) {
        // 1. 全平台公开库（status=0 AND visibility=1），按 ID 升序保证确定性
        LinkedHashSet<Long> visible = new LinkedHashSet<>(kbMapper.selectList(
                        Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                                .select(KbKnowledgeBaseEntity::getId)
                                .eq(KbKnowledgeBaseEntity::getStatus, 0)
                                .eq(KbKnowledgeBaseEntity::getVisibility, 1)
                                .orderByAsc(KbKnowledgeBaseEntity::getId))
                .stream().map(KbKnowledgeBaseEntity::getId).toList());
        // 2. 登录用户：+ 自己空间（多用户平台 D9 改写：按 owner_user_id 查用户自有空间，
        //    不能使用默认空间——默认空间属于系统博主，会导致登录用户越权看到他人私有库）
        if (userId != null) {
            Long workspaceId = workspaceApi.getWorkspaceIdByOwner(userId);
            if (workspaceId != null) {
                visible.addAll(kbMapper.selectList(
                                Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                                        .select(KbKnowledgeBaseEntity::getId)
                                        .eq(KbKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                                        .eq(KbKnowledgeBaseEntity::getStatus, 0)
                                        .orderByAsc(KbKnowledgeBaseEntity::getId))
                        .stream().map(KbKnowledgeBaseEntity::getId).toList());
            }
        }
        return new ArrayList<>(visible);
    }
}
