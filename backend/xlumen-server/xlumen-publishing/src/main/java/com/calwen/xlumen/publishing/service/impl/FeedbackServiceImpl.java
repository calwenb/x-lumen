package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.CreateFeedbackDTO;
import com.calwen.xlumen.publishing.entity.FeedbackEntity;
import com.calwen.xlumen.publishing.mapper.FeedbackMapper;
import com.calwen.xlumen.publishing.service.FeedbackService;
import com.calwen.xlumen.publishing.vo.FeedbackVO;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 读者纠错服务实现（F-1001）：匿名可提交（user_id 可空），同一 IP 60s 限流 2 次（Redis 降级放行）。
 * 工作空间取默认空间（MVP 单空间，决策 D9）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private static final Duration RATE_TTL = Duration.ofSeconds(60);
    private static final long RATE_LIMIT = 2;
    private static final String RATE_KEY = "xlumen:feedback:rate:%s";

    private static final int STATUS_PENDING = 1;

    @Resource
    private FeedbackMapper feedbackMapper;

    @Resource
    private ContentApi contentApi;

    @Resource
    private WorkspaceApi workspaceApi;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public FeedbackVO createFeedback(Long articleId, CreateFeedbackDTO dto) {
        Long workspaceId = workspaceApi.getDefaultWorkspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "博客空间未初始化");
        }
        checkRateLimit(dto.getIp());
        if (contentApi.getEditorArticle(workspaceId, articleId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }

        FeedbackEntity entity = new FeedbackEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setArticleId(articleId);
        entity.setUserId(WorkspaceContext.userId());
        entity.setPosition(dto.getPosition());
        entity.setProblem(dto.getProblem().trim());
        entity.setEvidence(dto.getEvidence());
        entity.setTrackNo(genTrackNo());
        entity.setStatus(STATUS_PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(entity);

        return FeedbackVO.builder()
                .trackNo(entity.getTrackNo()).position(entity.getPosition())
                .problem(entity.getProblem()).evidence(entity.getEvidence())
                .createdAt(entity.getCreatedAt()).build();
    }

    /** IP 限流：60s 内最多 2 次；Redis 异常降级放行（不阻断合法提交）。 */
    private void checkRateLimit(String ip) {
        if (StrUtil.isBlank(ip)) {
            return;
        }
        String key = String.format(RATE_KEY, ip);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, RATE_TTL);
            }
            if (count != null && count > RATE_LIMIT) {
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "提交过于频繁，请稍后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("纠错限流降级放行，ip={}", ip, e);
        }
    }

    /** 追踪号：雪花 ID 后 12 位，转大写字母数字。 */
    private String genTrackNo() {
        String snowflake = IdUtil.getSnowflakeNextIdStr();
        String trackNo = snowflake.length() > 12 ? snowflake.substring(snowflake.length() - 12) : snowflake;
        return trackNo.toUpperCase();
    }
}
