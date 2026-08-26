package com.calwen.xlumen.ai.controller;

import com.calwen.xlumen.ai.dto.ChatRequestDTO;
import com.calwen.xlumen.ai.service.ChatService;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 访客问答接口（匿名公开）：复用对话主链路 streamChat，访客会话挂默认工作空间（user_id 为空落库，
 * 会话按 user_id 隔离无串号）；按访客 IP 做 Redis 小时限流防刷（Redis 异常 fail-open）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/public/chat")
public class PublicChatController {

    private static final Logger log = LoggerFactory.getLogger(PublicChatController.class);

    /** 访客单 IP 小时提问上限。 */
    private static final long HOURLY_LIMIT = 60;

    /** 限流时间窗。 */
    private static final Duration WINDOW = Duration.ofHours(1);

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final ChatService chatService;
    private final StringRedisTemplate redisTemplate;

    public PublicChatController(ChatService chatService, StringRedisTemplate redisTemplate) {
        this.chatService = chatService;
        this.redisTemplate = redisTemplate;
    }

    /** 访客问答（SSE chunk/citation/done 事件），限流超限返回 429。 */
    @PostMapping
    public SseEmitter chat(@Valid @RequestBody ChatRequestDTO dto, HttpServletRequest request) {
        checkRateLimit(request);
        return chatService.streamChat(dto);
    }

    private void checkRateLimit(HttpServletRequest request) {
        String key = "xlumen:guestgpt:" + clientIp(request) + ":" + HOUR_FORMAT.format(LocalDateTime.now());
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            if (count != null && count > HOURLY_LIMIT) {
                redisTemplate.opsForValue().decrement(key);
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "提问过于频繁，请一小时后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.debug("访客限流不可用，fail-open", e);
        }
    }

    /** 客户端 IP：优先取反向代理传递的 X-Forwarded-For 首个，否则取 RemoteAddr。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}