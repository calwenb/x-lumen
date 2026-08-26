package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.vo.TracePageVO;
import com.calwen.xlumen.ai.vo.TraceSummaryVO;

/**
 * AI 调用追踪服务：LLM 调用埋点记录 + 管理面分页查询/用量统计。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface AiCallLogService {

    /**
     * 记录一次 LLM 调用。
     *
     * @param workspaceId  工作空间（可空，预留）
     * @param userId       发起用户（可空）
     * @param scene        场景
     * @param taskId       关联任务（可空）
     * @param requestType  调用形态
     * @param provider     供应商（空=脚本模型）
     * @param model        模型名
     * @param promptHash   Prompt 版本哈希（空串=无 System Prompt）
     * @param tokensIn     输入 Token
     * @param tokensOut    输出 Token
     * @param latencyMs    耗时（毫秒）
     * @param success      是否成功
     * @param degraded     是否降级
     * @param errorMsg     失败原因（可空）
     */
    void record(Long workspaceId, Long userId, AiScene scene, Long taskId, String requestType,
                String provider, String model, String promptHash, int tokensIn, int tokensOut,
                long latencyMs, boolean success, boolean degraded, String errorMsg);

    /** 管理面分页查询（时间倒序，可按场景/成败过滤）。 */
    TracePageVO page(Long workspaceId, String scene, Boolean success, long pageNo, long pageSize);

    /** 用量统计：今日调用/今日失败/今日按场景分布。 */
    TraceSummaryVO summary(Long workspaceId);
}