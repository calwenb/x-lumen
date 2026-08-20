package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.event.KnowledgePublishedEvent;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.ReleaseEntity;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReleaseMapper;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import com.calwen.xlumen.publishing.service.HotKnowledgeCacheService;
import com.calwen.xlumen.publishing.service.ReleaseService;
import com.calwen.xlumen.publishing.vo.ReleaseVO;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布服务实现（F-0904/F-0905）：立即/定时发布，发布成功发布 KnowledgePublishedEvent 进程内事件、
 * 写审计 KNOWLEDGE_PUBLISH 并失效热点缓存；定时发布幂等（状态 + 乐观锁双保险）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ReleaseServiceImpl implements ReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseServiceImpl.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private ReleaseMapper releaseMapper;

    @Resource
    private ReviewMapper reviewMapper;

    @Resource
    private ContentApi contentApi;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private ActivityLogService activityLogService;

    @Resource
    private HotKnowledgeCacheService hotKnowledgeCacheService;

    @Resource
    private KnowledgeApi knowledgeApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseVO release(CreateReleaseDTO dto) {
        Long workspaceId = WorkspaceContext.workspaceId();
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, dto.getKnowledgeId());
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        if (KnowledgeStatus.of(knowledge.getStatus()) != KnowledgeStatus.APPROVED) {
            throw new BizException(ErrorCode.CONFLICT, "仅审核通过的知识可发布");
        }
        // 归属兜底（决策 D16）：发布必须归属有效知识库，拦截历史孤儿数据（BUG-4 防线）
        if (knowledge.getKbId() == null
                || knowledgeApi.getKnowledgeBase(workspaceId, knowledge.getKbId()) == null) {
            throw new BizException(ErrorCode.CONFLICT, "知识未归属有效知识库，无法发布");
        }
        // 幂等兜底：同一知识同一提交版本只允许一次发布记录（F-0905）。
        // BUG-007 配套：发布入参版本是审核通过时的快照版本，approve 状态迁移经 @Version 乐观锁
        // 会把知识版本号 +1，故不在此强校验 dto.version 与知识当前版本相等；真正迁移的
        // expectedVersion 在 doRelease 内取知识当前版本（防覆盖并发），幂等由本查询保证。
        ReleaseEntity existing = releaseMapper.selectOne(Wrappers.<ReleaseEntity>lambdaQuery()
                .eq(ReleaseEntity::getWorkspaceId, workspaceId)
                .eq(ReleaseEntity::getKnowledgeId, dto.getKnowledgeId())
                .eq(ReleaseEntity::getVersion, dto.getVersion()));
        if (existing != null) {
            return toVO(existing);
        }
        if (!hasPassedAutoReview(workspaceId, dto.getKnowledgeId(), dto.getVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "发布前必须完成自动 AI 审核");
        }
        ReleaseEntity release = new ReleaseEntity();
        release.setWorkspaceId(workspaceId);
        release.setKnowledgeId(dto.getKnowledgeId());
        release.setKnowledgeTitle(knowledge.getTitle());
        release.setVersion(dto.getVersion());
        // 发布记录可见性快照取自知识库（决策 D16 可见性由库决定；null=按 1 公开记录）
        release.setVisibility(resolveKbVisibility(workspaceId, knowledge.getKbId()));
        release.setPublishAt(dto.getPublishAt());
        release.setStatus(STATUS_PENDING);
        release.setIdempotencyKey(IdUtil.getSnowflakeNextIdStr());
        release.setCreatedAt(LocalDateTime.now());
        releaseMapper.insert(release);

        // 立即发布（publishAt 空）；定时发布留待 PublishJob 幂等执行
        if (dto.getPublishAt() == null) {
            doRelease(release);
        }
        return toVO(release);
    }

    /** 新发布链路的最后一道服务端门禁，防止旧审核接口或直调发布接口绕过 Reviewer。 */
    private boolean hasPassedAutoReview(Long workspaceId, Long knowledgeId, Long version) {
        ReviewEntity review = reviewMapper.selectOne(Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getWorkspaceId, workspaceId)
                .eq(ReviewEntity::getKnowledgeId, knowledgeId)
                .eq(ReviewEntity::getVersion, version)
                .eq(ReviewEntity::getStatus, "APPROVED")
                .isNotNull(ReviewEntity::getAiTaskId)
                .orderByDesc(ReviewEntity::getUpdatedAt)
                .last("LIMIT 1"));
        if (review == null || StrUtil.isBlank(review.getAiResultJson())) return false;
        try {
            JSONArray issues = JSONUtil.parseArray(review.getAiResultJson());
            return issues.stream().noneMatch(item ->
                    "error".equalsIgnoreCase(JSONUtil.parseObj(item).getStr("severity")));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ReleaseVO findByKnowledgeVersion(Long knowledgeId, Long version) {
        ReleaseEntity release = releaseMapper.selectOne(Wrappers.<ReleaseEntity>lambdaQuery()
                .eq(ReleaseEntity::getWorkspaceId, WorkspaceContext.workspaceId())
                .eq(ReleaseEntity::getKnowledgeId, knowledgeId)
                .eq(ReleaseEntity::getVersion, version));
        return release == null ? null : toVO(release);
    }

    @Override
    public PageResult<ReleaseVO> listReleases(PageQueryDTO query) {
        Long workspaceId = WorkspaceContext.workspaceId();
        Page<ReleaseEntity> page = releaseMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()),
                Wrappers.<ReleaseEntity>lambdaQuery()
                        .eq(ReleaseEntity::getWorkspaceId, workspaceId)
                        .orderByDesc(ReleaseEntity::getCreatedAt));
        List<ReleaseVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.<ReleaseVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public void publishDue() {
        List<ReleaseEntity> due = releaseMapper.selectList(Wrappers.<ReleaseEntity>lambdaQuery()
                .eq(ReleaseEntity::getStatus, STATUS_PENDING)
                .le(ReleaseEntity::getPublishAt, LocalDateTime.now()));
        for (ReleaseEntity release : due) {
            try {
                doRelease(release);
            } catch (Exception e) {
                release.setStatus(STATUS_FAILED);
                releaseMapper.updateById(release);
                log.warn("定时发布失败，releaseId={}", release.getId(), e);
            }
        }
    }

    /** 幂等发布：知识迁移 PUBLISHED(6) + 发布记录 DONE + 事件/审计/缓存失效；版本冲突抛 409。 */
    private void doRelease(ReleaseEntity release) {
        Long workspaceId = release.getWorkspaceId();
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, release.getKnowledgeId());
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        boolean ok = contentApi.publishKnowledge(workspaceId, KnowledgePublishDTO.builder()
                .knowledgeId(release.getKnowledgeId())
                .expectedVersion(knowledge.getVersion())
                .targetStatus(KnowledgeStatus.PUBLISHED.getValue())
                // KB-3 发布目标按库（决策 D16）：知识已在库内，kbId/directoryId 由知识本身携带，无需前端传
                .kbId(knowledge.getKbId())
                .directoryId(knowledge.getDirectoryId())
                .publishedAt(LocalDateTime.now())
                .build());
        if (!ok) {
            throw new BizException(ErrorCode.CONFLICT, "发布失败，版本冲突");
        }
        release.setStatus(STATUS_DONE);
        release.setReleasedAt(LocalDateTime.now());
        releaseMapper.updateById(release);

        // 后置处理失败不影响发布主流程（事件/审计/缓存失效单独降级）
        try {
            eventPublisher.publishEvent(KnowledgePublishedEvent.builder()
                    .workspaceId(workspaceId).knowledgeId(release.getKnowledgeId()).version(knowledge.getVersion())
                    .title(knowledge.getTitle()).content(knowledge.getContent())
                    // KB-3 事件携带库 ID（决策 D13 索引按库切分）
                    .kbId(knowledge.getKbId())
                    .build());
            activityLogService.record(workspaceId, WorkspaceContext.userId(), WorkspaceContext.username(),
                    "KNOWLEDGE_PUBLISH", "KNOWLEDGE", release.getKnowledgeId(), null);
            hotKnowledgeCacheService.evictAll();
        } catch (Exception e) {
            log.warn("发布后置处理失败（事件/审计/缓存失效），releaseId={}", release.getId(), e);
        }
    }

    @Override
    public void unpublish(Long knowledgeId) {
        Long workspaceId = WorkspaceContext.workspaceId();
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        if (KnowledgeStatus.of(knowledge.getStatus()) != KnowledgeStatus.PUBLISHED) {
            throw new BizException(ErrorCode.CONFLICT, "仅已发布知识可下架");
        }
        // 乐观锁：知识当前版本即期望版本，冲突由 publishKnowledge 返回 false → 409
        boolean ok = contentApi.publishKnowledge(workspaceId, KnowledgePublishDTO.builder()
                .knowledgeId(knowledgeId).expectedVersion(knowledge.getVersion())
                .targetStatus(KnowledgeStatus.UNPUBLISHED.getValue()).build());
        if (!ok) {
            throw new BizException(ErrorCode.CONFLICT, "下架失败，版本冲突");
        }
        // 后置处理失败不影响下架主流程（出索引/缓存失效/审计单独降级）
        try {
            knowledgeApi.removeKnowledge(workspaceId, knowledgeId);
            hotKnowledgeCacheService.evictAll();
            activityLogService.record(workspaceId, WorkspaceContext.userId(), WorkspaceContext.username(),
                    "KNOWLEDGE_UNPUBLISH", "KNOWLEDGE", knowledgeId, null);
        } catch (Exception e) {
            log.warn("下架后置处理失败（出索引/缓存失效/审计），knowledgeId={}", knowledgeId, e);
        }
    }

    /** 发布记录可见性快照：取知识库可见性（决策 D16）；库不存在按 1（公开）记录。 */
    private Integer resolveKbVisibility(Long workspaceId, Long kbId) {
        if (kbId == null) {
            return 1;
        }
        KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBase(workspaceId, kbId);
        return kb == null ? 1 : kb.getVisibility();
    }

    private ReleaseVO toVO(ReleaseEntity r) {
        return ReleaseVO.builder()
                .id(r.getId()).knowledgeId(r.getKnowledgeId()).knowledgeTitle(r.getKnowledgeTitle())
                .version(r.getVersion()).visibility(r.getVisibility()).publishAt(r.getPublishAt())
                .releasedAt(r.getReleasedAt()).status(r.getStatus()).createdAt(r.getCreatedAt()).build();
    }
}
