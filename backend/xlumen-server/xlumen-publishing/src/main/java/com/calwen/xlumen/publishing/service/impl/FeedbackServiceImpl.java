package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
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
import java.util.List;

/**
 * 读者纠错服务实现：匿名可提交（user_id 可空），同一 IP 每分钟 1 条限流（M11，Redis 降级放行）。
 * 纠错面向任意空间公开且已发布的知识（多用户平台 D9）：可见性口径与公开详情一致，
 * 纠错记录落知识自身归属空间（先取知识、再取所属库 workspaceId），不使用默认空间。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private static final Duration RATE_TTL = Duration.ofSeconds(60);
    /** M11 契约：同 IP 每分钟 1 条，超限 429。count > 1 即拒绝第二次。 */
    private static final long RATE_LIMIT = 1;
    private static final String RATE_KEY = "xlumen:feedback:rate:%s";

    private static final int STATUS_PENDING = 1;

    @Resource
    private FeedbackMapper feedbackMapper;

    @Resource
    private ContentApi contentApi;

    @Resource
    private KnowledgeApi knowledgeApi;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public FeedbackVO createFeedback(Long knowledgeId, CreateFeedbackDTO dto) {
        // 可见性：访客视角公开库集合（仅公开且已发布知识可纠错），不放宽非公开/未发布/不存在的判定；
        // 存在性校验先于限流，失败不消耗额度，且错误语义不暴露资源存在性
        List<Long> visibleKbIds = knowledgeApi.resolveVisibleKbIds(null);
        KnowledgeDetailDTO knowledge = contentApi.getPublished(null, knowledgeId, visibleKbIds);
        if (knowledge == null || knowledge.getKbId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        // 纠错记录落知识自身归属空间（多用户平台 D9：不绑定默认空间）
        KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBaseById(knowledge.getKbId());
        if (kb == null || kb.getWorkspaceId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        checkRateLimit(dto.getIp());

        FeedbackEntity entity = new FeedbackEntity();
        entity.setWorkspaceId(kb.getWorkspaceId());
        entity.setKnowledgeId(knowledgeId);
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

    /** IP 限流：每分钟最多 1 条（M11 契约），仅在受理提交时计数（校验失败不调用）；
     *  increment 原子递增保证并发下同 IP 上限，超限 429；Redis 异常降级放行（不阻断合法提交）。 */
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
