package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.knowledge.dto.CreateKnowledgeBaseDTO;
import com.calwen.xlumen.knowledge.dto.UpdateKnowledgeBaseDTO;
import com.calwen.xlumen.knowledge.entity.KbKnowledgeBaseEntity;
import com.calwen.xlumen.knowledge.event.KbRecycleStatusEvent;
import com.calwen.xlumen.knowledge.event.KbVisibilityChangedEvent;
import com.calwen.xlumen.knowledge.mapper.KbKnowledgeBaseMapper;
import com.calwen.xlumen.knowledge.service.KnowledgeBaseService;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库服务实现（F-0308，决策 D16）：库 CRUD 与可见性切换落库，审计与跨模块联动走事件。
 * 时间戳由应用侧显式设置（无全局 MetaObjectHandler，见 identity 模块约定）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    @Resource
    private KbKnowledgeBaseMapper kbMapper;
    @Resource
    private ActivityLogService activityLogService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public KnowledgeBaseVO create(CreateKnowledgeBaseDTO dto) {
        Long workspaceId = requireWorkspace();
        String name = dto.getName().trim();
        // 名称空间内唯一（uk_kb_ws_name）：回收站内同名库同样占用唯一键，须先恢复或彻底删除
        Long count = kbMapper.selectCount(Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                .eq(KbKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                .eq(KbKnowledgeBaseEntity::getName, name));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "同名知识库已存在");
        }
        Integer visibility = dto.getVisibility() == null ? 0 : dto.getVisibility();
        if (visibility != 0 && visibility != 1) {
            throw new BizException(ErrorCode.INVALID_PARAM, "可见性参数非法（0 私有/1 公开）");
        }
        LocalDateTime now = LocalDateTime.now();
        KbKnowledgeBaseEntity entity = new KbKnowledgeBaseEntity();
        entity.setId(IdUtil.getSnowflakeNextId());
        entity.setWorkspaceId(workspaceId);
        entity.setName(name);
        entity.setIntro(dto.getIntro());
        entity.setCover(dto.getCover());
        entity.setVisibility(visibility);
        entity.setStatus(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        kbMapper.insert(entity);
        return toVO(entity, 0L);
    }

    @Override
    public KnowledgeBaseVO update(Long kbId, UpdateKnowledgeBaseDTO dto) {
        KbKnowledgeBaseEntity kb = getOwned(kbId);
        if (StrUtil.isNotBlank(dto.getName())) {
            String name = dto.getName().trim();
            if (!name.equals(kb.getName())) {
                Long count = kbMapper.selectCount(Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                        .eq(KbKnowledgeBaseEntity::getWorkspaceId, kb.getWorkspaceId())
                        .eq(KbKnowledgeBaseEntity::getName, name)
                        .ne(KbKnowledgeBaseEntity::getId, kbId));
                if (count != null && count > 0) {
                    throw new BizException(ErrorCode.CONFLICT, "同名知识库已存在");
                }
                kb.setName(name);
            }
        }
        // 简介/封面空值不覆盖
        if (StrUtil.isNotBlank(dto.getIntro())) {
            kb.setIntro(dto.getIntro());
        }
        if (StrUtil.isNotBlank(dto.getCover())) {
            kb.setCover(dto.getCover());
        }
        kb.setUpdatedAt(LocalDateTime.now());
        kbMapper.updateById(kb);
        return toVO(kb, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long kbId, String confirm) {
        if (!"CONFIRM".equals(confirm)) {
            throw new BizException(ErrorCode.CONFLICT, "删除知识库需要二次确认");
        }
        KbKnowledgeBaseEntity kb = getOwned(kbId);
        LocalDateTime now = LocalDateTime.now();
        kb.setStatus(1);
        kb.setDeletedAt(now);
        kb.setUpdatedAt(now);
        kbMapper.updateById(kb);
        // 方案 §7.2：库内知识连带进回收站——cnt_knowledge 属 content 模块（knowledge 依赖方向受限无法
        // 直连），由 content 侧监听 KbRecycleStatusEvent 后连带软删；关联定时发布（pub_release PENDING
        // →FAILED）与未决审核作废（pub_review PENDING→REJECTED）由 publishing 侧处理（本模块不依赖 publishing）。
        eventPublisher.publishEvent(KbRecycleStatusEvent.builder()
                .workspaceId(kb.getWorkspaceId())
                .kbId(kbId)
                .status(1)
                .build());
        log.info("知识库已移入回收站（库内知识连带软删待 content 侧监听处理）：workspaceId={}, kbId={}",
                kb.getWorkspaceId(), kbId);
    }

    @Override
    public KnowledgeBaseVO changeVisibility(Long kbId, Integer visibility) {
        if (visibility == null || (visibility != 0 && visibility != 1)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "可见性参数非法（0 私有/1 公开）");
        }
        KbKnowledgeBaseEntity kb = getOwned(kbId);
        kb.setVisibility(visibility);
        kb.setUpdatedAt(LocalDateTime.now());
        kbMapper.updateById(kb);
        try {
            // 审计（F-1202）：append-only，操作人/空间由 identity 侧落库
            activityLogService.record(kb.getWorkspaceId(), WorkspaceContext.userId(),
                    WorkspaceContext.username(), "KB_VISIBILITY_CHANGE", "KNOWLEDGE_BASE", kbId, null);
            // 缓存失效：公开读/检索缓存由 publishing 侧监听 KbVisibilityChangedEvent 按维度失效
            // （键分片 xlumen:knowledge:{kbId}:{directoryId}，决策 D16），knowledge 不依赖 publishing。
            eventPublisher.publishEvent(KbVisibilityChangedEvent.builder()
                    .workspaceId(kb.getWorkspaceId())
                    .kbId(kbId)
                    .visibility(visibility)
                    .build());
        } catch (Exception e) {
            log.warn("知识库可见性变更后置处理失败（审计/缓存失效事件），kbId={}", kbId, e);
        }
        return toVO(kb, 0L);
    }

    @Override
    public List<KnowledgeBaseVO> list(Long workspaceId) {
        if (workspaceId == null) {
            return List.of();
        }
        List<KbKnowledgeBaseEntity> kbs = kbMapper.selectList(Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                .eq(KbKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                .eq(KbKnowledgeBaseEntity::getStatus, 0)
                .orderByAsc(KbKnowledgeBaseEntity::getName));
        // knowledgeCount：cnt_knowledge 属 content 模块（knowledge 依赖方向受限无法直查），当前恒为 0，
        // 待 KB-3 content 改造实现 ContentApi.countKnowledgeByKbs 后由上层聚合补全（F-0308 列表展示）。
        return kbs.stream().map(e -> toVO(e, 0L)).toList();
    }

    @Override
    public KnowledgeBaseVO get(Long workspaceId, Long kbId) {
        if (kbId == null) {
            return null;
        }
        KbKnowledgeBaseEntity kb = kbMapper.selectById(kbId);
        if (kb == null || (workspaceId != null && !workspaceId.equals(kb.getWorkspaceId()))) {
            return null;
        }
        return toVO(kb, 0L);
    }

    @Override
    public KnowledgeBaseVO get(Long kbId) {
        return get(WorkspaceContext.workspaceId(), kbId);
    }

    /** 当前会话空间，未登录抛 401。 */
    private Long requireWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }

    /** 按 ID 取库并做会话空间归属校验，不存在/跨空间统一 404（不暴露资源存在性）。 */
    private KbKnowledgeBaseEntity getOwned(Long kbId) {
        KbKnowledgeBaseEntity kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId != null && !workspaceId.equals(kb.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    /** 实体转视图；knowledgeCount 暂由调用方给定（本模块无法统计 cnt_knowledge）。 */
    private KnowledgeBaseVO toVO(KbKnowledgeBaseEntity e, Long knowledgeCount) {
        return KnowledgeBaseVO.builder()
                .id(e.getId())
                .workspaceId(e.getWorkspaceId())
                .name(e.getName())
                .intro(e.getIntro())
                .cover(e.getCover())
                .visibility(e.getVisibility())
                .knowledgeCount(knowledgeCount)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
