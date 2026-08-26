package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.QuotaService;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 场景配额实现：日键 INCR 预占，超限回退并抛 429；失败 release 回退计数。
 * 配额读取 sceneConfigService.resolve 的 dailyQuota（0=不限）；Redis 异常 fail-open。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class QuotaServiceImpl implements QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaServiceImpl.class);
    private static final String KEY_PREFIX = "xlumen:quota:";

    private final StringRedisTemplate redisTemplate;
    private final SceneConfigService sceneConfigService;

    public QuotaServiceImpl(StringRedisTemplate redisTemplate, SceneConfigService sceneConfigService) {
        this.redisTemplate = redisTemplate;
        this.sceneConfigService = sceneConfigService;
    }

    @Override
    public void reserve(Long workspaceId, AiScene scene) {
        int quota = quotaOf(workspaceId, scene);
        if (workspaceId == null || quota <= 0) {
            return;
        }
        String key = key(workspaceId, scene);
        Long current;
        try {
            current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds()));
            }
        } catch (Exception e) {
            log.warn("配额计数不可用，跳过配额（fail-open）ws={} scene={}", workspaceId, scene, e);
            return;
        }
        if (current != null && current > quota) {
            redisTemplate.opsForValue().decrement(key);
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "今日「" + scene.name() + "」AI 调用已达配额上限 " + quota + " 次，请明日再试或调整配额");
        }
    }

    @Override
    public void settle(Long workspaceId, AiScene scene) {
        // 计数已在预占时保留，无附加动作
    }

    @Override
    public void release(Long workspaceId, AiScene scene) {
        if (workspaceId == null) {
            return;
        }
        try {
            Long val = redisTemplate.opsForValue().decrement(key(workspaceId, scene));
            if (val != null && val <= 0) {
                redisTemplate.delete(key(workspaceId, scene));
            }
        } catch (Exception e) {
            log.debug("配额释放失败（忽略）ws={} scene={}", workspaceId, scene, e);
        }
    }

    private int quotaOf(Long workspaceId, AiScene scene) {
        SceneModel sm = sceneConfigService.resolve(workspaceId, scene);
        return sm.getDailyQuota() == null ? 0 : sm.getDailyQuota();
    }

    private String key(Long workspaceId, AiScene scene) {
        return KEY_PREFIX + workspaceId + ":" + scene.name() + ":" + LocalDateTime.now().toLocalDate();
    }

    private long ttlSeconds() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
    }
}