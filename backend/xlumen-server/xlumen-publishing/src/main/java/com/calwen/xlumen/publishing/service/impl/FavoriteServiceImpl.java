package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.FavoriteEntity;
import com.calwen.xlumen.publishing.mapper.FavoriteMapper;
import com.calwen.xlumen.publishing.service.FavoriteService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识收藏服务实现（F-0212）：toggle 幂等（唯一键）+ 我的收藏分页（可见性过滤后组装）。
 * 登录态接口的 workspaceId/userId 全部来自 WorkspaceContext（JWT claims，F-0104）。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final int FAVORITE_ON = 1;
    private static final int FAVORITE_OFF = 0;

    @Resource
    private FavoriteMapper favoriteMapper;

    @Resource
    private ContentApi contentApi;

    @Resource
    private KnowledgeApi knowledgeApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long knowledgeId) {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        Long workspaceId = WorkspaceContext.workspaceId();
        FavoriteEntity favorite = favoriteMapper.selectOne(Wrappers.<FavoriteEntity>lambdaQuery()
                // workspaceId 可空=跨空间（公开读用户态判定，D9 改写）
                .eq(workspaceId != null, FavoriteEntity::getWorkspaceId, workspaceId)
                .eq(FavoriteEntity::getKnowledgeId, knowledgeId)
                .eq(FavoriteEntity::getUserId, userId));
        if (favorite == null) {
            favorite = new FavoriteEntity();
            favorite.setId(IdUtil.getSnowflakeNextId());
            favorite.setWorkspaceId(workspaceId);
            favorite.setKnowledgeId(knowledgeId);
            favorite.setUserId(userId);
            favorite.setStatus(FAVORITE_ON);
            favoriteMapper.insert(favorite);
            return true;
        }
        boolean next = favorite.getStatus() == null || favorite.getStatus() != FAVORITE_ON;
        favorite.setStatus(next ? FAVORITE_ON : FAVORITE_OFF);
        favoriteMapper.updateById(favorite);
        return next;
    }

    @Override
    public boolean isFavorited(Long workspaceId, Long knowledgeId, Long userId) {
        if (userId == null) {
            return false;
        }
        FavoriteEntity favorite = favoriteMapper.selectOne(Wrappers.<FavoriteEntity>lambdaQuery()
                .eq(FavoriteEntity::getWorkspaceId, workspaceId)
                .eq(FavoriteEntity::getKnowledgeId, knowledgeId)
                .eq(FavoriteEntity::getUserId, userId));
        return favorite != null && favorite.getStatus() != null && favorite.getStatus() == FAVORITE_ON;
    }

    @Override
    public Map<Long, Long> countFavorites(Long workspaceId, List<Long> knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return Map.of();
        }
        List<FavoriteEntity> rows = favoriteMapper.selectList(Wrappers.<FavoriteEntity>lambdaQuery()
                .select(FavoriteEntity::getKnowledgeId)
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）
                .eq(workspaceId != null, FavoriteEntity::getWorkspaceId, workspaceId)
                .in(FavoriteEntity::getKnowledgeId, knowledgeIds)
                .eq(FavoriteEntity::getStatus, FAVORITE_ON));
        return rows.stream().collect(Collectors.groupingBy(
                FavoriteEntity::getKnowledgeId, Collectors.counting()));
    }

    @Override
    public PageResult<KnowledgeCardVO> listFavorites(PageQueryDTO query) {
        Long userId = WorkspaceContext.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 可见库集合按身份推导（F-0407 单一实现，决策 D13）：与公开列表同一可见性口径
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(userId);
        Page<FavoriteEntity> page = favoriteMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()),
                Wrappers.<FavoriteEntity>lambdaQuery()
                        .eq(FavoriteEntity::getUserId, userId)
                        .eq(FavoriteEntity::getStatus, FAVORITE_ON)
                        .orderByDesc(FavoriteEntity::getCreatedAt));
        // MVP 取舍：先分页查收藏行，再逐条经 getPublished 做可见性过滤（库被删/转私有/下架后不出现在
        // 收藏列表），内存过滤后本页条数可能略少于 pageSize；total 仍按收藏行数返回，V2 改 SQL JOIN 精确分页
        Map<Long, KnowledgeDetailDTO> visibleKnowledges = new LinkedHashMap<>();
        for (FavoriteEntity favorite : page.getRecords()) {
            // workspaceId 传 null=跨空间回查（收藏时知识可能属于其他空间）；visibleKbIds 承担可见性过滤
            KnowledgeDetailDTO knowledge = contentApi.getPublished(null, favorite.getKnowledgeId(), visibleKbIds);
            if (knowledge != null) {
                visibleKnowledges.put(favorite.getKnowledgeId(), knowledge);
            }
        }
        Map<Long, String> kbNames = loadKbNames(visibleKnowledges.values().stream()
                .map(KnowledgeDetailDTO::getKbId).toList());
        List<KnowledgeCardVO> records = new ArrayList<>();
        for (FavoriteEntity favorite : page.getRecords()) {
            KnowledgeDetailDTO knowledge = visibleKnowledges.get(favorite.getKnowledgeId());
            if (knowledge == null) {
                continue;
            }
            records.add(KnowledgeCardVO.builder()
                    .id(knowledge.getId()).title(knowledge.getTitle()).summary(knowledge.getSummary())
                    .authorName(knowledge.getAuthorName())
                    .kbId(knowledge.getKbId()).kbName(kbNames.get(knowledge.getKbId()))
                    .directoryId(knowledge.getDirectoryId())
                    .tags(knowledge.getTags()).viewCount(knowledge.getViewCount())
                    .readMinutes(knowledge.getReadMinutes())
                    .favoritedAt(favorite.getCreatedAt())
                    .publishedAt(knowledge.getPublishedAt()).build());
        }
        return PageResult.<KnowledgeCardVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    /** 库名批量查询（跨空间只读，与 PublicKnowledgeServiceImpl 同款写法）：去重 kbId 逐个查详情，规避 N+1。 */
    private Map<Long, String> loadKbNames(List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (Long kbId : kbIds.stream().filter(Objects::nonNull).distinct().toList()) {
            KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBaseById(kbId);
            names.put(kbId, kb == null ? null : kb.getName());
        }
        return names;
    }
}
