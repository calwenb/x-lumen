package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.event.ArticlePublishedEvent;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ArticlePublishDTO;
import com.calwen.xlumen.content.api.dto.EditorArticleDTO;
import com.calwen.xlumen.content.enums.ArticleStatus;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.ReleaseEntity;
import com.calwen.xlumen.publishing.mapper.ReleaseMapper;
import com.calwen.xlumen.publishing.service.HotArticleCacheService;
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
 * 发布服务实现（F-0904/F-0905）：立即/定时发布，发布成功发布 ArticlePublishedEvent 进程内事件、
 * 写审计 ARTICLE_PUBLISH 并失效热点缓存；定时发布幂等（状态 + 乐观锁双保险）。
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
    private ContentApi contentApi;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private ActivityLogService activityLogService;

    @Resource
    private HotArticleCacheService hotArticleCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseVO release(CreateReleaseDTO dto) {
        Long workspaceId = WorkspaceContext.workspaceId();
        EditorArticleDTO article = contentApi.getEditorArticle(workspaceId, dto.getArticleId());
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        if (ArticleStatus.of(article.getStatus()) != ArticleStatus.APPROVED) {
            throw new BizException(ErrorCode.CONFLICT, "仅审核通过的文章可发布");
        }
        if (!dto.getVersion().equals(article.getVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "版本冲突");
        }
        Long exists = releaseMapper.selectCount(Wrappers.<ReleaseEntity>lambdaQuery()
                .eq(ReleaseEntity::getWorkspaceId, workspaceId)
                .eq(ReleaseEntity::getArticleId, dto.getArticleId())
                .eq(ReleaseEntity::getVersion, dto.getVersion()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该版本已提交发布");
        }
        ReleaseEntity release = new ReleaseEntity();
        release.setWorkspaceId(workspaceId);
        release.setArticleId(dto.getArticleId());
        release.setArticleTitle(article.getTitle());
        release.setVersion(dto.getVersion());
        release.setVisibility(dto.getVisibility());
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

    /** 幂等发布：文章迁移 PUBLISHED(6) + 发布记录 DONE + 事件/审计/缓存失效；版本冲突抛 409。 */
    private void doRelease(ReleaseEntity release) {
        Long workspaceId = release.getWorkspaceId();
        EditorArticleDTO article = contentApi.getEditorArticle(workspaceId, release.getArticleId());
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        boolean ok = contentApi.publishArticle(workspaceId, ArticlePublishDTO.builder()
                .articleId(release.getArticleId())
                .expectedVersion(article.getVersion())
                .targetStatus(ArticleStatus.PUBLISHED.getValue())
                .visibility(release.getVisibility())
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
            eventPublisher.publishEvent(ArticlePublishedEvent.builder()
                    .workspaceId(workspaceId).articleId(release.getArticleId()).version(article.getVersion())
                    .title(article.getTitle()).content(article.getContent()).visibility(release.getVisibility())
                    .build());
            activityLogService.record(workspaceId, WorkspaceContext.userId(), WorkspaceContext.username(),
                    "ARTICLE_PUBLISH", "ARTICLE", release.getArticleId(), null);
            hotArticleCacheService.evictAll();
        } catch (Exception e) {
            log.warn("发布后置处理失败（事件/审计/缓存失效），releaseId={}", release.getId(), e);
        }
    }

    private ReleaseVO toVO(ReleaseEntity r) {
        return ReleaseVO.builder()
                .id(r.getId()).articleId(r.getArticleId()).articleTitle(r.getArticleTitle())
                .version(r.getVersion()).visibility(r.getVisibility()).publishAt(r.getPublishAt())
                .releasedAt(r.getReleasedAt()).status(r.getStatus()).createdAt(r.getCreatedAt()).build();
    }
}
