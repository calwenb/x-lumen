package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;

import java.util.List;
import java.util.function.Supplier;

/**
 * 热点读缓存（F-1301）：公开知识详情与标签聚合的 cache-aside 缓存。
 * KB-3 缓存分片（方案 §3.4）：详情键 xlumen:knowledge:detail:{id}（多用户公开读跨空间，
 * 访客视角全平台共享，键不含身份与空间），失效按库维度（发布/下架/可见性变更调用 evictByKb，
 * MVP 简化全量删 detail 前缀）；分类聚合缓存删除（category 废弃，决策 D16 改目录树）；
 * 列表不缓存（MVP 直接查库，V2 加 xlumen:knowledge:list:* 分片）。
 * 缓存不可用（Redis 异常）时降级回源，不向上抛错。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface HotKnowledgeCacheService {

    /**
     * 公开知识详情缓存（键 xlumen:knowledge:detail:{id}，TTL 10min；空值哨兵 30s）。
     * 仅访客视角调用（登录态含私有库可见范围，直查回源避免跨身份串读，见实现方 javadoc）。
     *
     * @param knowledgeId 知识 ID
     * @param loader      回源函数（缓存未命中时调用）
     * @return 知识详情；不存在返回 null
     */
    KnowledgeDetailVO getKnowledge(Long knowledgeId, Supplier<KnowledgeDetailVO> loader);

    /**
     * 标签聚合缓存（键 xlumen:tags，TTL 5min）：跨空间全平台聚合。
     *
     * @param loader 回源函数
     * @return 标签聚合列表
     */
    List<CategoryCountDTO> getTags(Supplier<List<CategoryCountDTO>> loader);

    /**
     * 按库维度失效热点缓存（方案 §3.4）：发布/下架/库可见性变更后调用。
     * MVP 简化：全量删详情键前缀（xlumen:knowledge:detail:*），V2 按 kbId 维度精确失效。
     *
     * @param kbId 知识库 ID
     */
    void evictByKb(Long kbId);

    /**
     * 失效全部热点缓存（发布/下架后调用，删 Redis 键前缀 xlumen:knowledge:detail:/
     * xlumen:knowledge:list:（预留）/xlumen:tags:）。
     */
    void evictAll();
}
