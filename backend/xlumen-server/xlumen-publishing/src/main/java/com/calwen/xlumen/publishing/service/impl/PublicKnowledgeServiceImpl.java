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
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客前台公开读服务实现（F-0201/F-0202）：公开读编排 + 阅读量 Redis 防刷（F-0203）。
 * 阅读量防刷：访客指纹 + 知识 ID 组成 Redis 键，24 小时有效期内只计一次（短期状态，决策 D6）。
 * KB-3 起按身份推导可见库集合（F-0407 单一实现 resolveVisibleKbIds，决策 D13）：
 * 列表/详情均按库级可见性过滤，kbName 由本层批量填充（卡片库 badge）。
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
    private ContentApi contentApi;

    @Resource
    private KnowledgeApi knowledgeApi;

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
        // 多用户公开读跨空间聚合（D9 改写）：范围由可见库集合精确表达，不绑定默认空间
        Long userId = WorkspaceContext.userId();
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(userId);
        if (visibleKbIds == null || visibleKbIds.isEmpty()) {
            // 无可见库：直接返回空页，不查询内容库
            return PageResult.<KnowledgeCardVO>builder()
                    .total(0L).pageNo(query.getPageNo()).pageSize(query.getPageSize()).records(List.of()).build();
        }
        // 入参封装为跨模块稳定类型（BACKEND.md §5.2）：category 已废弃（决策 D16 改目录树），
        // 改传 kbId/directoryId 库级筛选 + visibleKbIds 可见库集合；workspaceId 传 null=跨空间聚合
        com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO queryDto =
                com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO.builder()
                        .keyword(query.getKeyword()).tag(query.getTag())
                        .kbId(query.getKbId()).directoryId(query.getDirectoryId())
                        .visibleKbIds(visibleKbIds)
                        .pageNo(query.getPageNo()).pageSize(query.getPageSize()).build();
        ContentPageResult<PublishedKnowledgeDTO> page = contentApi.listPublished(null, queryDto);
        List<Long> ids = page.getRecords().stream().map(PublishedKnowledgeDTO::getId).toList();
        // kbName 按库批量取回（跨空间只读，只对本页去重后的 kbId 查询，规避 N+1，BACKEND.md §18）
        Map<Long, String> kbNames = loadKbNames(page.getRecords().stream()
                .map(PublishedKnowledgeDTO::getKbId).toList());
        // 批量统计一次取回（避免 N+1，BACKEND.md §18）；跨空间聚合时互动统计不带空间过滤
        Map<Long, Long> commentCounts = commentService.countComments(null, ids);
        Map<Long, Long> likeCounts = likeService.countLikes(null, ids);
        List<KnowledgeCardVO> records = page.getRecords().stream()
                .map(a -> KnowledgeCardVO.builder()
                        .id(a.getId()).title(a.getTitle()).summary(a.getSummary()).authorName(a.getAuthorName())
                        .kbId(a.getKbId()).kbName(kbNames.get(a.getKbId())).directoryId(a.getDirectoryId())
                        .tags(a.getTags()).viewCount(a.getViewCount())
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
        // 多用户公开读跨空间（D9 改写）：可见库集合按身份推导（userId 可空=访客，F-0407 决策 D13）
        Long userId = WorkspaceContext.userId();
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(userId);
        // 热点读缓存（F-1301）：仅访客视角按 id 缓存（键 xlumen:knowledge:detail:{id}，
        // KB-3 分片改造，方案 §3.4）。登录态直查回源：可见范围含私有库，缓存键不含身份，
        // 避免私有库内容跨身份串读（决策 D13 库级可见性）。
        KnowledgeDetailVO vo = userId == null
                ? hotKnowledgeCacheService.getKnowledge(knowledgeId,
                        () -> buildKnowledgeDetail(knowledgeId, visibleKbIds))
                : buildKnowledgeDetail(knowledgeId, visibleKbIds);
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在或未公开");
        }
        // liked 为用户态，不缓存，命中缓存后按当前用户重算（避免跨用户串号）
        vo.setLiked(userId != null && likeService.isLiked(null, knowledgeId, userId));
        return vo;
    }

    /** 组装知识详情（缓存回源）：互动统计批量取回，kbName 由本层填充，liked 置 false 由外层重算。 */
    private KnowledgeDetailVO buildKnowledgeDetail(Long knowledgeId, List<Long> visibleKbIds) {
        // 新签名：可见库集合过滤（content agent 已改，决策 D13）；workspaceId=null 跨空间聚合
        KnowledgeDetailDTO knowledge = contentApi.getPublished(null, knowledgeId, visibleKbIds);
        if (knowledge == null) {
            return null;
        }
        String kbName = knowledge.getKbId() == null ? null
                : loadKbNames(List.of(knowledge.getKbId())).get(knowledge.getKbId());
        long commentCount = commentService.countComments(null, List.of(knowledgeId))
                .getOrDefault(knowledgeId, 0L);
        long likeCount = likeService.countLikes(null, List.of(knowledgeId))
                .getOrDefault(knowledgeId, 0L);
        return KnowledgeDetailVO.builder()
                .id(knowledge.getId()).title(knowledge.getTitle()).summary(knowledge.getSummary()).content(knowledge.getContent())
                .authorName(knowledge.getAuthorName())
                .kbId(knowledge.getKbId()).kbName(kbName).directoryId(knowledge.getDirectoryId())
                .tags(knowledge.getTags())
                .viewCount(knowledge.getViewCount()).readMinutes(knowledge.getReadMinutes())
                .commentCount(commentCount).likeCount(likeCount).liked(false)
                .publishedAt(knowledge.getPublishedAt()).updatedAt(knowledge.getUpdatedAt()).build();
    }

    @Override
    public boolean recordView(Long knowledgeId, String visitorKey) {
        if (StrUtil.isBlank(visitorKey)) {
            return false;
        }
        // 跨空间公开读：知识实际归属空间经内容库回查后自增（避免绑定默认空间）
        Long workspaceId = resolveKnowledgeWorkspace(knowledgeId);
        if (workspaceId == null) {
            return false;
        }
        // setIfAbsent 原子判定：24 小时窗口内同访客只计一次
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(VIEW_KEY_PREFIX + workspaceId + ":" + knowledgeId + ":" + visitorKey, "1", VIEW_DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            return false;
        }
        return contentApi.incrementViewCount(workspaceId, knowledgeId);
    }

    @Override
    public List<CategoryCountDTO> listTags() {
        // 标签聚合跨空间（D9 改写）：content 侧全量统计已发布且不在回收站的知识标签
        return hotKnowledgeCacheService.getTags(() -> contentApi.listTags(null));
    }

    /** 库名批量查询（跨空间只读）：只对去重后的 kbId 逐个查知识库详情（getKnowledgeBaseById），
     *  避免按条数 N+1；库不存在返回 null（卡片不显示 badge）。 */
    private Map<Long, String> loadKbNames(List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (Long kbId : kbIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBaseById(kbId);
            names.put(kbId, kb == null ? null : kb.getName());
        }
        return names;
    }

    /** 知识实际归属空间（跨空间公开读阅读量自增用）：经公开详情回查 kbId → 库 workspaceId。 */
    private Long resolveKnowledgeWorkspace(Long knowledgeId) {
        Long userId = WorkspaceContext.userId();
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(userId);
        KnowledgeDetailDTO knowledge = contentApi.getPublished(null, knowledgeId, visibleKbIds);
        if (knowledge == null || knowledge.getKbId() == null) {
            return null;
        }
        KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBaseById(knowledge.getKbId());
        return kb == null ? null : kb.getWorkspaceId();
    }

}
