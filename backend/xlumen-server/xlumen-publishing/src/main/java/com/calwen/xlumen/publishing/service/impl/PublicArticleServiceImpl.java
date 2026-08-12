package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ArticleDetailDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.PublishedArticleDTO;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.ArticleCardVO;
import com.calwen.xlumen.publishing.dto.ArticleDetailVO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.engagement.service.EngagementService;
import com.calwen.xlumen.publishing.service.PublicArticleService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 博客前台公开读服务实现（F-0201/F-0202）：公开读编排 + 阅读量 Redis 防刷（F-0203）。
 * 阅读量防刷：访客指纹 + 文章 ID 组成 Redis 键，24 小时有效期内只计一次（短期状态，决策 D6）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class PublicArticleServiceImpl implements PublicArticleService {

    /** 阅读量防刷窗口（小时）。 */
    private static final Duration VIEW_DEDUP_TTL = Duration.ofHours(24);
    private static final String VIEW_KEY_PREFIX = "xlumen:view:";

    @Resource
    private WorkspaceApi workspaceApi;

    @Resource
    private ContentApi contentApi;

    @Resource
    private EngagementService engagementService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<ArticleCardVO> listArticles(String keyword, String category, String tag, long pageNo, long pageSize) {
        Long workspaceId = defaultWorkspace();
        ContentPageResult<PublishedArticleDTO> page =
                contentApi.listPublished(workspaceId, keyword, category, tag, pageNo, pageSize);
        List<Long> ids = page.records().stream().map(PublishedArticleDTO::id).toList();
        // 批量统计一次取回（避免 N+1，BACKEND.md §18）
        Map<Long, Long> commentCounts = engagementService.countComments(workspaceId, ids);
        Map<Long, Long> likeCounts = engagementService.countLikes(workspaceId, ids);
        List<ArticleCardVO> records = page.records().stream()
                .map(a -> new ArticleCardVO(a.id(), a.title(), a.summary(), a.authorName(), a.category(), a.tags(),
                        a.viewCount(), a.readMinutes(), commentCounts.getOrDefault(a.id(), 0L),
                        likeCounts.getOrDefault(a.id(), 0L), a.publishedAt()))
                .toList();
        return new PageResult<>(page.total(), page.pageNo(), page.pageSize(), records);
    }

    @Override
    public ArticleDetailVO getArticle(Long articleId, Long userId) {
        Long workspaceId = defaultWorkspace();
        ArticleDetailDTO article = contentApi.getPublished(workspaceId, articleId);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在或未公开");
        }
        long commentCount = engagementService.countComments(workspaceId, List.of(articleId))
                .getOrDefault(articleId, 0L);
        long likeCount = engagementService.countLikes(workspaceId, List.of(articleId))
                .getOrDefault(articleId, 0L);
        boolean liked = userId != null && engagementService.isLiked(workspaceId, articleId, userId);
        return new ArticleDetailVO(article.id(), article.title(), article.summary(), article.content(),
                article.authorName(), article.category(), article.tags(), article.viewCount(),
                article.readMinutes(), commentCount, likeCount, liked, article.publishedAt(), article.updatedAt());
    }

    @Override
    public boolean recordView(Long articleId, String visitorKey) {
        if (StrUtil.isBlank(visitorKey)) {
            return false;
        }
        Long workspaceId = defaultWorkspace();
        // setIfAbsent 原子判定：24 小时窗口内同访客只计一次
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(VIEW_KEY_PREFIX + workspaceId + ":" + articleId + ":" + visitorKey, "1", VIEW_DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            return false;
        }
        return contentApi.incrementViewCount(workspaceId, articleId);
    }

    @Override
    public List<CategoryCountDTO> listCategories() {
        return contentApi.listCategories(defaultWorkspace());
    }

    @Override
    public List<CategoryCountDTO> listTags() {
        return contentApi.listTags(defaultWorkspace());
    }

    /** 默认公开空间（MVP 单空间，决策 D9）；无空间视为系统未初始化。 */
    private Long defaultWorkspace() {
        Long workspaceId = workspaceApi.getDefaultWorkspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "博客空间未初始化");
        }
        return workspaceId;
    }
}
