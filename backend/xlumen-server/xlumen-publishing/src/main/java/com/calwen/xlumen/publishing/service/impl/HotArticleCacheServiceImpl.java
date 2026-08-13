package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.ArticleDetailVO;
import com.calwen.xlumen.publishing.service.HotArticleCacheService;
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
 * 序列化用 Jackson 3（tools.jackson，LocalDateTime 默认 ISO 格式可解析），与 Boot 4 技术基线一致。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class HotArticleCacheServiceImpl implements HotArticleCacheService {

    private static final Logger log = LoggerFactory.getLogger(HotArticleCacheServiceImpl.class);

    /** 空值哨兵：缓存文章不存在，避免缓存穿透。 */
    private static final String NULL_SENTINEL = "NULL";

    private static final Duration ARTICLE_TTL = Duration.ofMinutes(10);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(30);
    private static final Duration AGG_TTL = Duration.ofMinutes(5);

    private static final String ARTICLE_KEY = "xlumen:article:%d:%d";
    private static final String CATEGORY_KEY = "xlumen:categories:%d";
    private static final String TAG_KEY = "xlumen:tags:%d";

    /** Jackson 3 序列化器（知识模块同款用法，LocalDateTime 默认 ISO）。 */
    private static final JsonMapper JSON = new JsonMapper();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public ArticleDetailVO getArticle(Long workspaceId, Long articleId, Supplier<ArticleDetailVO> loader) {
        String key = String.format(ARTICLE_KEY, workspaceId, articleId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return NULL_SENTINEL.equals(cached) ? null : JSON.readValue(cached, ArticleDetailVO.class);
            }
            ArticleDetailVO vo = loader.get();
            if (vo == null) {
                stringRedisTemplate.opsForValue().set(key, NULL_SENTINEL, EMPTY_TTL);
            } else {
                stringRedisTemplate.opsForValue().set(key, JSON.writeValueAsString(vo), ARTICLE_TTL);
            }
            return vo;
        } catch (Exception e) {
            log.warn("文章详情缓存降级回源，key={}", key, e);
            return loader.get();
        }
    }

    @Override
    public List<CategoryCountDTO> getCategories(Long workspaceId, Supplier<List<CategoryCountDTO>> loader) {
        return getAgg(String.format(CATEGORY_KEY, workspaceId), loader);
    }

    @Override
    public List<CategoryCountDTO> getTags(Long workspaceId, Supplier<List<CategoryCountDTO>> loader) {
        return getAgg(String.format(TAG_KEY, workspaceId), loader);
    }

    @Override
    public void evictAll() {
        String[] patterns = {"xlumen:article:*", "xlumen:articles:list:*", "xlumen:categories:*", "xlumen:tags:*"};
        try {
            for (String pattern : patterns) {
                Set<String> keys = stringRedisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
            }
        } catch (Exception e) {
            log.warn("缓存失效失败（降级忽略），patterns={}", Arrays.toString(patterns), e);
        }
    }

    /** 分类/标签聚合缓存：命中返回，未命中回源回填（5min）。 */
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
