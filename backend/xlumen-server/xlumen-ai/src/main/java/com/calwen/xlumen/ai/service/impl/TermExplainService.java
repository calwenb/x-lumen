package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.common.context.WorkspaceContext;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 术语解释服务：AI 生成 1~2 句解释并按术语短缓存（24 小时），
 * 访客可用（workspaceId 为空走默认模型，配额跳过）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class TermExplainService {

    private static final String CACHE_PREFIX = "xlumen:term:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String PROMPT = "你是小光，一名知识讲解助手。请用 1~2 句话解释给定术语，"
            + "语句要通俗准确；若你掌握该术语在 Web 开发/内容平台领域的背景，可顺带一句价值说明。只输出解释文本，不要前缀。";

    private final ChatRuntime chatRuntime;
    private final StringRedisTemplate redisTemplate;

    public TermExplainService(ChatRuntime chatRuntime, StringRedisTemplate redisTemplate) {
        this.chatRuntime = chatRuntime;
        this.redisTemplate = redisTemplate;
    }

    /** 解释术语：缓存命中直接返回；未命中走 AI 生成并缓存。 */
    public TermExplainResult explain(String term) {
        String key = cacheKey(term);
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (Exception ignored) {
            // Redis 异常降级为直接生成
        }
        if (StrUtil.isNotBlank(cached)) {
            return new TermExplainResult(term, cached, true);
        }
        String explanation = chatRuntime.chat(WorkspaceContext.workspaceId(), AiScene.WRITING,
                List.of(new SystemMessage(PROMPT), new UserMessage(term)), 0.4, 300).trim();
        if (StrUtil.isBlank(explanation)) {
            throw new com.calwen.xlumen.common.exception.BizException(
                    com.calwen.xlumen.common.web.ErrorCode.SERVICE_UNAVAILABLE, "AI 解释生成失败，请稍后重试");
        }
        try {
            redisTemplate.opsForValue().set(key, explanation, CACHE_TTL);
        } catch (Exception ignored) {
            // 缓存失败不影响返回
        }
        return new TermExplainResult(term, explanation, false);
    }

    private String cacheKey(String term) {
        String normalized = term.trim().toLowerCase();
        return CACHE_PREFIX + (normalized.length() > 60 ? normalized.substring(0, 60) : normalized);
    }

    /** 解释结果。 */
    public record TermExplainResult(String term, String explanation, boolean fromCache) {
    }
}