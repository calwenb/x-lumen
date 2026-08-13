package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.enums.AiTaskStatus;
import com.calwen.xlumen.ai.mapper.AiTaskMapper;
import com.calwen.xlumen.ai.service.AiTaskDispatcher;
import com.calwen.xlumen.ai.service.AiTaskService;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * AI 任务底座实现（F-1302）：幂等提交落库 QUEUED、状态流转、Redis 进度、有限重试、启动恢复。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private static final Logger log = LoggerFactory.getLogger(AiTaskServiceImpl.class);

    /** 任务进度 Redis key 前缀。 */
    private static final String PROGRESS_KEY_PREFIX = "xlumen:task:progress:";
    /** 进度 TTL。 */
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);
    /** 最大重试次数。 */
    private static final int MAX_RETRY = 3;

    private final AiTaskMapper aiTaskMapper;
    private final StringRedisTemplate redisTemplate;
    private final AiTaskDispatcher dispatcher;

    public AiTaskServiceImpl(AiTaskMapper aiTaskMapper,
                             StringRedisTemplate redisTemplate,
                             @Lazy AiTaskDispatcher dispatcher) {
        this.aiTaskMapper = aiTaskMapper;
        this.redisTemplate = redisTemplate;
        this.dispatcher = dispatcher;
    }

    @Override
    public Long submit(Long workspaceId, Long userId, AiScene scene, String inputJson, String idempotencyKey) {
        if (workspaceId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "缺少工作空间");
        }
        if (StrUtil.isNotBlank(idempotencyKey)) {
            AiTaskEntity existing = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTaskEntity>()
                    .eq(AiTaskEntity::getWorkspaceId, workspaceId)
                    .eq(AiTaskEntity::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing.getId();
            }
        }
        AiTaskEntity task = new AiTaskEntity();
        task.setWorkspaceId(workspaceId);
        task.setUserId(userId);
        task.setScene(scene.name());
        task.setStatus(AiTaskStatus.QUEUED.name());
        task.setInputJson(inputJson);
        task.setErrorMsg("");
        task.setRetryCount(0);
        task.setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey);
        aiTaskMapper.insert(task);
        dispatcher.dispatch(task);
        return task.getId();
    }

    @Override
    public AiTaskEntity get(Long workspaceId, Long taskId) {
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getId, taskId)
                .eq(AiTaskEntity::getWorkspaceId, workspaceId));
    }

    @Override
    public void markRunning(Long taskId) {
        aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getId, taskId)
                .eq(AiTaskEntity::getStatus, AiTaskStatus.QUEUED.name())
                .set(AiTaskEntity::getStatus, AiTaskStatus.RUNNING.name()));
    }

    @Override
    public void complete(Long taskId, String resultJson) {
        AiTaskEntity task = aiTaskMapper.selectById(taskId);
        if (task == null || AiTaskStatus.valueOf(task.getStatus()).isTerminal()) {
            return;
        }
        task.setStatus(AiTaskStatus.COMPLETED.name());
        task.setResultJson(resultJson);
        task.setErrorMsg("");
        aiTaskMapper.updateById(task);
    }

    @Override
    public void fail(Long taskId, String errorMsg) {
        AiTaskEntity task = aiTaskMapper.selectById(taskId);
        if (task == null || AiTaskStatus.valueOf(task.getStatus()).isTerminal()) {
            return;
        }
        task.setStatus(AiTaskStatus.FAILED.name());
        task.setErrorMsg(errorMsg == null ? "" : errorMsg);
        aiTaskMapper.updateById(task);
    }

    @Override
    public boolean retry(Long workspaceId, Long taskId) {
        AiTaskEntity task = get(workspaceId, taskId);
        if (task == null) {
            return false;
        }
        AiTaskStatus status = AiTaskStatus.valueOf(task.getStatus());
        if (status != AiTaskStatus.FAILED) {
            return false;
        }
        int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retry >= MAX_RETRY) {
            return false;
        }
        task.setStatus(AiTaskStatus.QUEUED.name());
        task.setRetryCount(retry + 1);
        task.setErrorMsg("");
        aiTaskMapper.updateById(task);
        dispatcher.dispatch(task);
        return true;
    }

    @Override
    public void publishProgress(Long taskId, int progress) {
        redisTemplate.opsForValue().set(PROGRESS_KEY_PREFIX + taskId, String.valueOf(progress), PROGRESS_TTL);
    }

    @Override
    public int resetRunningToQueued() {
        return aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getStatus, AiTaskStatus.RUNNING.name())
                .set(AiTaskEntity::getStatus, AiTaskStatus.QUEUED.name()));
    }

    @Override
    public List<AiTaskEntity> listQueued() {
        return aiTaskMapper.selectList(new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getStatus, AiTaskStatus.QUEUED.name())
                .orderByAsc(AiTaskEntity::getCreatedAt));
    }

    /** 启动恢复：RUNNING 重置 QUEUED，并重新派发未完成的 QUEUED 任务。 */
    @PostConstruct
    public void recover() {
        int reset = resetRunningToQueued();
        if (reset > 0) {
            log.info("启动恢复：{} 个 RUNNING 任务重置为 QUEUED", reset);
        }
        List<AiTaskEntity> queued = listQueued();
        for (AiTaskEntity task : queued) {
            dispatcher.dispatch(task);
        }
        if (!queued.isEmpty()) {
            log.info("启动恢复：重新派发 {} 个 QUEUED 任务", queued.size());
        }
    }
}
