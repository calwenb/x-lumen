package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;

import java.util.List;
import java.util.function.Supplier;

/**
 * 热点读缓存（F-1301）：公开知识详情与分类/标签聚合的 cache-aside 缓存。
 * 缓存不可用（Redis 异常）时降级回源，不向上抛错；列表与互动统计不缓存（避免一致性复杂度）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface HotKnowledgeCacheService {

    /**
     * 公开知识详情缓存（键 xlumen:knowledge:{ws}:{id}，TTL 10min；空值哨兵 30s）。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId 知识 ID
     * @param loader      回源函数（缓存未命中时调用）
     * @return 知识详情；不存在返回 null
     */
    KnowledgeDetailVO getKnowledge(Long workspaceId, Long knowledgeId, Supplier<KnowledgeDetailVO> loader);

    /**
     * 分类聚合缓存（键 xlumen:categories:{ws}，TTL 5min）。
     *
     * @param workspaceId 工作空间 ID
     * @param loader      回源函数
     * @return 分类聚合列表
     */
    List<CategoryCountDTO> getCategories(Long workspaceId, Supplier<List<CategoryCountDTO>> loader);

    /**
     * 标签聚合缓存（键 xlumen:tags:{ws}，TTL 5min）。
     *
     * @param workspaceId 工作空间 ID
     * @param loader      回源函数
     * @return 标签聚合列表
     */
    List<CategoryCountDTO> getTags(Long workspaceId, Supplier<List<CategoryCountDTO>> loader);

    /**
     * 失效全部热点缓存（发布/下架后调用，删 Redis 键前缀 xlumen:knowledge:/xlumen:knowledge:list:/xlumen:categories:/xlumen:tags:）。
     */
    void evictAll();
}
