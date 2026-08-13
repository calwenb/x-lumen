package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;

import java.util.List;

/**
 * AI 任务底座（F-1302）：任务事实存 MySQL（决策 D6），进度写 Redis 短期状态。
 * 幂等提交、状态流转（QUEUED→RUNNING→COMPLETED/FAILED）、有限重试、启动恢复。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface AiTaskService {

    /**
     * 提交任务（幂等）：idempotencyKey 相同返回已有任务；落库 QUEUED 后提交线程池。
     *
     * @param workspaceId    工作空间 ID
     * @param userId         发起用户 ID
     * @param scene          场景
     * @param inputJson      任务入参（JSON 文本）
     * @param idempotencyKey 业务幂等键
     * @return 任务 ID
     */
    Long submit(Long workspaceId, Long userId, AiScene scene, String inputJson, String idempotencyKey);

    /**
     * 查询任务（按工作空间隔离，越权返回 null）。
     *
     * @param workspaceId 工作空间 ID
     * @param taskId      任务 ID
     * @return 任务实体或 null
     */
    AiTaskEntity get(Long workspaceId, Long taskId);

    /**
     * 标记 RUNNING（仅 QUEUED→RUNNING）。
     *
     * @param taskId 任务 ID
     */
    void markRunning(Long taskId);

    /**
     * 完成任务：写 resultJson 并置 COMPLETED（终止态后不再流转）。
     *
     * @param taskId     任务 ID
     * @param resultJson 结果（JSON 文本）
     */
    void complete(Long taskId, String resultJson);

    /**
     * 失败任务：写 errorMsg 并置 FAILED（终止态后不再流转）。
     *
     * @param taskId   任务 ID
     * @param errorMsg 失败原因（对外脱敏）
     */
    void fail(Long taskId, String errorMsg);

    /**
     * 有限重试：FAILED 且 retry_count&lt;3 时重置 QUEUED 并重新派发。
     *
     * @param workspaceId 工作空间 ID
     * @param taskId      任务 ID
     * @return 是否可重试并已重置
     */
    boolean retry(Long workspaceId, Long taskId);

    /**
     * 写任务进度到 Redis（TTL 1h）。
     *
     * @param taskId   任务 ID
     * @param progress 进度 0-100
     */
    void publishProgress(Long taskId, int progress);

    /**
     * 启动恢复：RUNNING 重置为 QUEUED。
     *
     * @return 重置条数
     */
    int resetRunningToQueued();

    /**
     * 查询所有 QUEUED 任务（启动恢复重新派发）。
     *
     * @return 排队任务列表
     */
    List<AiTaskEntity> listQueued();
}
