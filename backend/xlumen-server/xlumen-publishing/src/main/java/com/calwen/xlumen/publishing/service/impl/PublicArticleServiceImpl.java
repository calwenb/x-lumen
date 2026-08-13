package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
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
import com.calwen.xlumen.publishing.dto.ArticleQueryDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.CommentService;
import com.calwen.xlumen.publishing.service.HotArticleCacheService;
import com.calwen.xlumen.publishing.service.LikeService;
import com.calwen.xlumen.publishing.service.PublicArticleService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    private CommentService commentService;

    @Resource
    private LikeService likeService;

    @Resource
    private HotArticleCacheService hotArticleCacheService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<ArticleCardVO> listArticles(ArticleQueryDTO query) {
        Long workspaceId = defaultWorkspace();
        // 入参封装为跨模块稳定类型（BACKEND.md §5.2）
        com.calwen.xlumen.content.api.dto.ArticleQueryDTO queryDto =
                com.calwen.xlumen.content.api.dto.ArticleQueryDTO.builder()
                        .keyword(query.getKeyword()).category(query.getCategory()).tag(query.getTag())
                        .pageNo(query.getPageNo()).pageSize(query.getPageSize()).build();
        ContentPageResult<PublishedArticleDTO> page = contentApi.listPublished(workspaceId, queryDto);
        List<Long> ids = page.getRecords().stream().map(PublishedArticleDTO::getId).toList();
        // 批量统计一次取回（避免 N+1，BACKEND.md §18）
        Map<Long, Long> commentCounts = commentService.countComments(workspaceId, ids);
        Map<Long, Long> likeCounts = likeService.countLikes(workspaceId, ids);
        List<ArticleCardVO> records = page.getRecords().stream()
                .map(a -> ArticleCardVO.builder()
                        .id(a.getId()).title(a.getTitle()).summary(a.getSummary()).authorName(a.getAuthorName())
                        .category(a.getCategory()).tags(a.getTags()).viewCount(a.getViewCount())
                        .readMinutes(a.getReadMinutes())
                        .commentCount(commentCounts.getOrDefault(a.getId(), 0L))
                        .likeCount(likeCounts.getOrDefault(a.getId(), 0L))
                        .publishedAt(a.getPublishedAt()).build())
                .toList();
        return PageResult.<ArticleCardVO>builder()
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).records(records).build();
    }

    @Override
    public ArticleDetailVO getArticle(Long articleId) {
        Long workspaceId = defaultWorkspace();
        ArticleDetailVO vo = hotArticleCacheService.getArticle(workspaceId, articleId,
                () -> buildArticleDetail(workspaceId, articleId));
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在或未公开");
        }
        // liked 为用户态，不缓存，命中缓存后按当前用户重算（避免跨用户串号）
        Long userId = WorkspaceContext.userId();
        vo.setLiked(userId != null && likeService.isLiked(workspaceId, articleId, userId));
        return vo;
    }

    /** 组装文章详情（缓存回源）：互动统计批量取回，liked 置 false 由外层重算。 */
    private ArticleDetailVO buildArticleDetail(Long workspaceId, Long articleId) {
        ArticleDetailDTO article = contentApi.getPublished(workspaceId, articleId);
        if (article == null) {
            return null;
        }
        long commentCount = commentService.countComments(workspaceId, List.of(articleId))
                .getOrDefault(articleId, 0L);
        long likeCount = likeService.countLikes(workspaceId, List.of(articleId))
                .getOrDefault(articleId, 0L);
        return ArticleDetailVO.builder()
                .id(article.getId()).title(article.getTitle()).summary(article.getSummary()).content(article.getContent())
                .authorName(article.getAuthorName()).category(article.getCategory()).tags(article.getTags())
                .viewCount(article.getViewCount()).readMinutes(article.getReadMinutes())
                .commentCount(commentCount).likeCount(likeCount).liked(false)
                .publishedAt(article.getPublishedAt()).updatedAt(article.getUpdatedAt()).build();
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
        Long workspaceId = defaultWorkspace();
        return hotArticleCacheService.getCategories(workspaceId, () -> contentApi.listCategories(workspaceId));
    }

    @Override
    public List<CategoryCountDTO> listTags() {
        Long workspaceId = defaultWorkspace();
        return hotArticleCacheService.getTags(workspaceId, () -> contentApi.listTags(workspaceId));
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
