package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiCallLogEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.AiCallLogMapper;
import com.calwen.xlumen.ai.service.AiCallLogService;
import com.calwen.xlumen.ai.vo.AiCallLogVO;
import com.calwen.xlumen.ai.vo.TracePageVO;
import com.calwen.xlumen.ai.vo.TraceSummaryVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 调用追踪实现：写入 ai_call_log，费用按每千 Token 单价估算（XLUMEN_TRACE_COST_PER_1K）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class AiCallLogServiceImpl implements AiCallLogService {

    private final AiCallLogMapper callLogMapper;
    private final AiProperties aiProperties;

    public AiCallLogServiceImpl(AiCallLogMapper callLogMapper, AiProperties aiProperties) {
        this.callLogMapper = callLogMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public void record(Long workspaceId, Long userId, AiScene scene, Long taskId, String requestType,
                       String provider, String model, String promptHash, int tokensIn, int tokensOut,
                       long latencyMs, boolean success, boolean degraded, String errorMsg) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setScene(scene == null ? "" : scene.name());
        entity.setTaskId(taskId);
        entity.setRequestType(requestType);
        entity.setProvider(provider == null ? "" : provider);
        entity.setModel(model == null ? "" : model);
        entity.setPromptHash(promptHash == null ? "" : promptHash);
        entity.setTokensIn(tokensIn);
        entity.setTokensOut(tokensOut);
        entity.setEstCost(estimateCost(tokensIn + tokensOut));
        entity.setSuccess(success);
        entity.setDegraded(degraded);
        entity.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        entity.setErrorMsg(StrUtil.sub(errorMsg == null ? "" : errorMsg, 0, 500));
        callLogMapper.insert(entity);
    }

    @Override
    public TracePageVO page(Long workspaceId, String scene, Boolean success, long pageNo, long pageSize) {
        long size = Math.min(Math.max(pageSize, 1), 100);
        long offset = Math.max(pageNo - 1, 0) * size;
        LambdaQueryWrapper<AiCallLogEntity> query = new LambdaQueryWrapper<AiCallLogEntity>()
                .eq(workspaceId != null, AiCallLogEntity::getWorkspaceId, workspaceId)
                .eq(StrUtil.isNotBlank(scene), AiCallLogEntity::getScene, scene)
                .eq(success != null, AiCallLogEntity::getSuccess, success);
        long total = callLogMapper.selectCount(query.clone());
        List<AiCallLogEntity> rows = callLogMapper.selectList(query.clone()
                .orderByDesc(AiCallLogEntity::getCreatedAt)
                .last("LIMIT " + offset + "," + size));
        return TracePageVO.builder()
                .records(rows.stream().map(this::toVO).toList())
                .total(total)
                .build();
    }

    @Override
    public TraceSummaryVO summary(Long workspaceId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = callLogMapper.selectCount(new LambdaQueryWrapper<AiCallLogEntity>()
                .eq(workspaceId != null, AiCallLogEntity::getWorkspaceId, workspaceId)
                .ge(AiCallLogEntity::getCreatedAt, todayStart));
        long todayFailed = callLogMapper.selectCount(new LambdaQueryWrapper<AiCallLogEntity>()
                .eq(workspaceId != null, AiCallLogEntity::getWorkspaceId, workspaceId)
                .eq(AiCallLogEntity::getSuccess, false)
                .ge(AiCallLogEntity::getCreatedAt, todayStart));
        // 分组统计用 QueryWrapper + 显式别名（LambdaQueryWrapper 不支持聚合别名）
        QueryWrapper<AiCallLogEntity> group = new QueryWrapper<AiCallLogEntity>()
                .select("scene", "COUNT(*) AS cnt")
                .eq(workspaceId != null, "workspace_id", workspaceId)
                .ge("created_at", todayStart)
                .groupBy("scene");
        List<Map<String, Object>> groups = callLogMapper.selectMaps(group);
        List<TraceSummaryVO.SceneCountVO> byScene = groups.stream()
                .map(m -> new TraceSummaryVO.SceneCountVO(
                        String.valueOf(m.getOrDefault("scene", "")), ((Number) m.getOrDefault("cnt", 0L)).longValue()))
                .toList();
        return TraceSummaryVO.builder()
                .todayCount(todayCount)
                .todayFailed(todayFailed)
                .todayByScene(byScene)
                .build();
    }

    private BigDecimal estimateCost(int totalTokens) {
        BigDecimal price = BigDecimal.valueOf(aiProperties.getTraceCostPer1k());
        return BigDecimal.valueOf(totalTokens)
                .multiply(price)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    private AiCallLogVO toVO(AiCallLogEntity e) {
        return AiCallLogVO.builder()
                .id(e.getId())
                .scene(e.getScene())
                .requestType(e.getRequestType())
                .provider(e.getProvider())
                .model(e.getModel())
                .promptHash(e.getPromptHash())
                .tokensIn(e.getTokensIn() == null ? 0 : e.getTokensIn())
                .tokensOut(e.getTokensOut() == null ? 0 : e.getTokensOut())
                .estCost(e.getEstCost())
                .success(Boolean.TRUE.equals(e.getSuccess()))
                .degraded(Boolean.TRUE.equals(e.getDegraded()))
                .latencyMs(e.getLatencyMs() == null ? 0 : e.getLatencyMs())
                .errorMsg(e.getErrorMsg())
                .createdAt(e.getCreatedAt())
                .build();
    }
}