package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;
import com.calwen.xlumen.publishing.service.HotKnowledgeCacheService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 热点读缓存实现（F-1301）：cache-aside 模式，Redis 异常降级回源不抛错。
 * KB-3 缓存分片（方案 §3.4）：详情键 xlumen:knowledge:detail:{ws}:{id}；分类缓存删除
 * （category 废弃）；标签键 xlumen:tags:{ws} 保留；列表不缓存（V2 加 list 分片预留）。
 * 序列化用 Jackson 3（tools.jackson，LocalDateTime 默认 ISO 格式可解析），与 Boot 4 技术基线一致。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class HotKnowledgeCacheServiceImpl implements HotKnowledgeCacheService {

    private static final Logger log = LoggerFactory.getLogger(HotKnowledgeCacheServiceImpl.class);

    /** 空值哨兵：缓存知识不存在，避免缓存穿透。 */
    private static final String NULL_SENTINEL = "NULL";

    private static final Duration KNOWLEDGE_TTL = Duration.ofMinutes(10);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(30);
    private static final Duration AGG_TTL = Duration.ofMinutes(5);

    private static final String KNOWLEDGE_KEY = "xlumen:knowledge:detail:%d";
    private static final String TAG_KEY = "xlumen:tags";

    /** 失效模式（方案 §3.4）：详情分片前缀 + 列表分片前缀（预留）+ 标签。 */
    private static final String[] EVICT_PATTERNS = {
            "xlumen:knowledge:detail:*", "xlumen:knowledge:list:*", "xlumen:tags:*"
    };

    /** Jackson 3 序列化器（知识模块同款用法，LocalDateTime 默认 ISO）。 */
    private static final JsonMapper JSON = new JsonMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public KnowledgeDetailVO getKnowledge(Long knowledgeId, Supplier<KnowledgeDetailVO> loader) {
        String key = String.format(KNOWLEDGE_KEY, knowledgeId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return NULL_SENTINEL.equals(cached) ? null : JSON.readValue(cached, KnowledgeDetailVO.class);
            }
            KnowledgeDetailVO vo = loader.get();
            if (vo == null) {
                stringRedisTemplate.opsForValue().set(key, NULL_SENTINEL, EMPTY_TTL);
            } else {
                stringRedisTemplate.opsForValue().set(key, JSON.writeValueAsString(vo), KNOWLEDGE_TTL);
            }
            return vo;
        } catch (Exception e) {
            log.warn("知识详情缓存降级回源，key={}", key, e);
            return loader.get();
        }
    }

    @Override
    public List<CategoryCountDTO> getTags(Supplier<List<CategoryCountDTO>> loader) {
        return getAgg(TAG_KEY, loader);
    }

    @Override
    public void evictByKb(Long kbId) {
        // MVP 简化：按库维度失效时全量删详情键前缀（详情键不含 kbId 维度，V2 改
        // xlumen:knowledge:detail:{kbId}:{ws}:{id} 后按 kbId 精确失效）
        evict("xlumen:knowledge:detail:*");
    }

    @Override
    public void evictAll() {
        try {
            for (String pattern : EVICT_PATTERNS) {
                Set<String> keys = stringRedisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
            }
        } catch (Exception e) {
            log.warn("缓存失效失败（降级忽略），patterns={}", Arrays.toString(EVICT_PATTERNS), e);
        }
    }

    /** 单模式失效（evictByKb 用）：删指定前缀全部键，失败降级忽略。 */
    private void evict(String pattern) {
        try {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("缓存失效失败（降级忽略），pattern={}", pattern, e);
        }
    }

    /** 标签聚合缓存：命中返回，未命中回源回填（5min）。 */
    private List<CategoryCountDTO> getAgg(String key, Supplier<List<CategoryCountDTO>> loader) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Arrays.asList(JSON.readValue(cached, CategoryCountDTO[].class));
            }
            List<CategoryCountDTO> result = loader.get();
            if (result == null) {
                result = List.of();
            }
            stringRedisTemplate.opsForValue().set(key, JSON.writeValueAsString(result), AGG_TTL);
            return result;
        } catch (Exception e) {
            log.warn("聚合缓存降级回源，key={}", key, e);
            return loader.get();
        }
    }
}
