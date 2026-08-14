package com.calwen.xlumen.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.dto.PageResult;
import com.calwen.xlumen.knowledge.entity.KbKnowledgeBaseEntity;
import com.calwen.xlumen.knowledge.event.KbPurgedEvent;
import com.calwen.xlumen.knowledge.event.KbRecycleStatusEvent;
import com.calwen.xlumen.knowledge.mapper.KbKnowledgeBaseMapper;
import com.calwen.xlumen.knowledge.service.RecycleBinService;
import com.calwen.xlumen.knowledge.vo.RecycleBinItemVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站服务实现（F-0305）：库侧回收（kb_knowledge_base.status=1）本模块直接承载；
 * 知识侧（cnt_knowledge.recycle_status=1）数据属 content 模块，knowledge 依赖方向受限（content→ai→
 * knowledge 环）无法直连，列表/恢复/彻底删除的接入待 KB-3 content 改造实现 ContentApi 契约后完成。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Slf4j
@Service
public class RecycleBinServiceImpl implements RecycleBinService {

    /** 分页上限（PageQueryDTO 约定，服务层统一截断）。 */
    private static final long MAX_PAGE_SIZE = 100;

    @Resource
    private KbKnowledgeBaseMapper kbMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<RecycleBinItemVO> list(String type, PageQueryDTO query) {
        Long workspaceId = requireWorkspace();
        String normalized = type == null ? "" : type.trim();
        if (!normalized.isEmpty() && !"kb".equals(normalized) && !"knowledge".equals(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "回收站类型参数非法（kb/knowledge）");
        }
        long pageNo = Math.max(1L, query.getPageNo());
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1L, query.getPageSize()));
        if ("knowledge".equals(normalized)) {
            // 知识回收站条目在 cnt_knowledge（content 模块），本模块无法直查；由 KB-3 content 改造实现
            // ContentApi.listRecycledKnowledge 后经上层聚合接入（回收站「知识」Tab），当前返回空页。
            log.warn("回收站知识列表待 KB-3 content 改造接入（ContentApi.listRecycledKnowledge），当前返回空页：workspaceId={}", workspaceId);
            return PageResult.<RecycleBinItemVO>builder()
                    .total(0L).pageNo(pageNo).pageSize(pageSize).records(List.of()).build();
        }
        // type=kb 或空=全部（当前知识部分为空，聚合结果即库条目）
        Page<KbKnowledgeBaseEntity> page = kbMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<KbKnowledgeBaseEntity>lambdaQuery()
                        .eq(KbKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                        .eq(KbKnowledgeBaseEntity::getStatus, 1)
                        .orderByDesc(KbKnowledgeBaseEntity::getDeletedAt));
        List<RecycleBinItemVO> records = page.getRecords().stream()
                .map(e -> RecycleBinItemVO.builder()
                        .type("kb")
                        .id(e.getId())
                        .name(e.getName())
                        .deletedAt(e.getDeletedAt())
                        .build())
                .toList();
        return PageResult.<RecycleBinItemVO>builder()
                .total(page.getTotal())
                .pageNo(page.getCurrent())
                .pageSize(page.getSize())
                .records(records)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(String type, Long id) {
        String normalized = type == null ? "" : type.trim();
        Long workspaceId = requireWorkspace();
        if ("kb".equals(normalized)) {
            KbKnowledgeBaseEntity kb = getOwnedRecycled(workspaceId, id);
            if (kb.getStatus() == null || kb.getStatus() != 1) {
                return; // 幂等：已恢复正常库无需处理
            }
            kb.setStatus(0);
            kb.setDeletedAt(null);
            kb.setUpdatedAt(LocalDateTime.now());
            kbMapper.updateById(kb);
            // 方案 §7.2 整体恢复：库内知识连带恢复由 content 侧监听 KbRecycleStatusEvent(status=0) 完成
            eventPublisher.publishEvent(KbRecycleStatusEvent.builder()
                    .workspaceId(workspaceId)
                    .kbId(id)
                    .status(0)
                    .build());
            log.info("知识库已从回收站恢复（库内知识连带恢复待 content 侧监听处理）：workspaceId={}, kbId={}", workspaceId, id);
            return;
        }
        if ("knowledge".equals(normalized)) {
            // 知识恢复需 cnt_knowledge 数据与冲突判定（原目录已删→挂库根；原库已彻底删除→409），
            // 由 KB-3 content 改造实现 ContentApi.getRecycledKnowledge/restoreKnowledge 后接入。
            log.warn("回收站知识恢复待 KB-3 content 改造接入（ContentApi.restoreKnowledge）：workspaceId={}, knowledgeId={}", workspaceId, id);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "知识回收操作暂不可用，请稍后重试");
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "回收站类型参数非法（kb/knowledge）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purge(String type, Long id, String confirm) {
        if (!"CONFIRM".equals(confirm)) {
            throw new BizException(ErrorCode.CONFLICT, "彻底删除需要二次确认");
        }
        String normalized = type == null ? "" : type.trim();
        Long workspaceId = requireWorkspace();
        if ("kb".equals(normalized)) {
            KbKnowledgeBaseEntity kb = getOwnedRecycled(workspaceId, id);
            kbMapper.deleteById(kb.getId());
            // 方案 §7.2 物理级联删知识：由 content 侧监听 KbPurgedEvent 完成（并逐条触发索引清理
            // KnowledgeApi.removeKnowledge；publishing 侧据此失效公开读缓存）
            eventPublisher.publishEvent(KbPurgedEvent.builder()
                    .workspaceId(workspaceId)
                    .kbId(id)
                    .build());
            log.info("知识库已彻底删除（库内知识物理级联删除待 content 侧监听处理）：workspaceId={}, kbId={}", workspaceId, id);
            return;
        }
        if ("knowledge".equals(normalized)) {
            // 知识物理删除在 cnt_knowledge（content 模块），由 KB-3 content 改造实现
            // ContentApi.purgeKnowledge 后接入（索引清理经 KnowledgeApi.removeKnowledge）。
            log.warn("回收站知识彻底删除待 KB-3 content 改造接入（ContentApi.purgeKnowledge）：workspaceId={}, knowledgeId={}", workspaceId, id);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "知识回收操作暂不可用，请稍后重试");
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "回收站类型参数非法（kb/knowledge）");
    }

    /** 当前会话空间，未登录抛 401。 */
    private Long requireWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }

    /** 按 ID 取库并校验会话空间归属，不存在/跨空间统一 404。 */
    private KbKnowledgeBaseEntity getOwnedRecycled(Long workspaceId, Long id) {
        KbKnowledgeBaseEntity kb = kbMapper.selectById(id);
        if (kb == null || !workspaceId.equals(kb.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }
}
