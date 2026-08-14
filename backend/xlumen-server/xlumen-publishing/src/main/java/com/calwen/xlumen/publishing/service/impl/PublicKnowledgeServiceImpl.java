package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;
import com.calwen.xlumen.publishing.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.CommentService;
import com.calwen.xlumen.publishing.service.HotKnowledgeCacheService;
import com.calwen.xlumen.publishing.service.LikeService;
import com.calwen.xlumen.publishing.service.PublicKnowledgeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 博客前台公开读服务实现（F-0201/F-0202）：公开读编排 + 阅读量 Redis 防刷（F-0203）。
 * 阅读量防刷：访客指纹 + 知识 ID 组成 Redis 键，24 小时有效期内只计一次（短期状态，决策 D6）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class PublicKnowledgeServiceImpl implements PublicKnowledgeService {

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
    private HotKnowledgeCacheService hotKnowledgeCacheService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<KnowledgeCardVO> listKnowledge(KnowledgeQueryDTO query) {
        Long workspaceId = defaultWorkspace();
        // 入参封装为跨模块稳定类型（BACKEND.md §5.2）
        com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO queryDto =
                com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO.builder()
                        .keyword(query.getKeyword()).category(query.getCategory()).tag(query.getTag())
                        .pageNo(query.getPageNo()).pageSize(query.getPageSize()).build();
        ContentPageResult<PublishedKnowledgeDTO> page = contentApi.listPublished(workspaceId, queryDto);
        List<Long> ids = page.getRecords().stream().map(PublishedKnowledgeDTO::getId).toList();
        // 批量统计一次取回（避免 N+1，BACKEND.md §18）
        Map<Long, Long> commentCounts = commentService.countComments(workspaceId, ids);
        Map<Long, Long> likeCounts = likeService.countLikes(workspaceId, ids);
        List<KnowledgeCardVO> records = page.getRecords().stream()
                .map(a -> KnowledgeCardVO.builder()
                        .id(a.getId()).title(a.getTitle()).summary(a.getSummary()).authorName(a.getAuthorName())
                        .category(a.getCategory()).tags(a.getTags()).viewCount(a.getViewCount())
                        .readMinutes(a.getReadMinutes())
                        .commentCount(commentCounts.getOrDefault(a.getId(), 0L))
                        .likeCount(likeCounts.getOrDefault(a.getId(), 0L))
                        .publishedAt(a.getPublishedAt()).build())
                .toList();
        return PageResult.<KnowledgeCardVO>builder()
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).records(records).build();
    }

    @Override
    public KnowledgeDetailVO getKnowledge(Long knowledgeId) {
        Long workspaceId = defaultWorkspace();
        KnowledgeDetailVO vo = hotKnowledgeCacheService.getKnowledge(workspaceId, knowledgeId,
                () -> buildKnowledgeDetail(workspaceId, knowledgeId));
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在或未公开");
        }
        // liked 为用户态，不缓存，命中缓存后按当前用户重算（避免跨用户串号）
        Long userId = WorkspaceContext.userId();
        vo.setLiked(userId != null && likeService.isLiked(workspaceId, knowledgeId, userId));
        return vo;
    }

    /** 组装知识详情（缓存回源）：互动统计批量取回，liked 置 false 由外层重算。 */
    private KnowledgeDetailVO buildKnowledgeDetail(Long workspaceId, Long knowledgeId) {
        KnowledgeDetailDTO knowledge = contentApi.getPublished(workspaceId, knowledgeId);
        if (knowledge == null) {
            return null;
        }
        long commentCount = commentService.countComments(workspaceId, List.of(knowledgeId))
                .getOrDefault(knowledgeId, 0L);
        long likeCount = likeService.countLikes(workspaceId, List.of(knowledgeId))
                .getOrDefault(knowledgeId, 0L);
        return KnowledgeDetailVO.builder()
                .id(knowledge.getId()).title(knowledge.getTitle()).summary(knowledge.getSummary()).content(knowledge.getContent())
                .authorName(knowledge.getAuthorName()).category(knowledge.getCategory()).tags(knowledge.getTags())
                .viewCount(knowledge.getViewCount()).readMinutes(knowledge.getReadMinutes())
                .commentCount(commentCount).likeCount(likeCount).liked(false)
                .publishedAt(knowledge.getPublishedAt()).updatedAt(knowledge.getUpdatedAt()).build();
    }

    @Override
    public boolean recordView(Long knowledgeId, String visitorKey) {
        if (StrUtil.isBlank(visitorKey)) {
            return false;
        }
        Long workspaceId = defaultWorkspace();
        // setIfAbsent 原子判定：24 小时窗口内同访客只计一次
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(VIEW_KEY_PREFIX + workspaceId + ":" + knowledgeId + ":" + visitorKey, "1", VIEW_DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            return false;
        }
        return contentApi.incrementViewCount(workspaceId, knowledgeId);
    }

    @Override
    public List<CategoryCountDTO> listCategories() {
        Long workspaceId = defaultWorkspace();
        return hotKnowledgeCacheService.getCategories(workspaceId, () -> contentApi.listCategories(workspaceId));
    }

    @Override
    public List<CategoryCountDTO> listTags() {
        Long workspaceId = defaultWorkspace();
        return hotKnowledgeCacheService.getTags(workspaceId, () -> contentApi.listTags(workspaceId));
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
