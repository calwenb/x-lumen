package com.calwen.xlumen.ai.api;

import com.calwen.xlumen.ai.api.dto.SubmitTaskDTO;
import com.calwen.xlumen.ai.api.vo.TaskResultVO;

/**
 * AI 模块对外接口（BACKEND.md §5.2）：异步任务提交与查询（F-1302）+ 知识最新摘要查询（F-0808）。
 * 调用方（content/publishing）提交写作/审校任务后轮询结果；AI 不反向依赖调用方（BACKEND.md §14）。
 * 实现：service/impl/AiApiImpl（M06 任务底座 + M07 场景执行器）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface AiApi {

    /**
     * 提交 AI 异步任务（幂等：idempotencyKey 相同返回已有任务）。
     *
     * @param dto 任务入参
     * @return 任务 ID
     */
    Long submitTask(SubmitTaskDTO dto);

    /**
     * 查询任务结果（调用方轮询）。
     *
     * @param workspaceId 工作空间 ID（越权返回 null）
     * @param taskId      任务 ID
     * @return 任务结果或 null
     */
    TaskResultVO queryTask(Long workspaceId, Long taskId);

    /**
     * 查询知识最新 AI 摘要（F-0808，ai_enhance_result scene=SUMMARY 按 created_at 倒序取首条）。
     *
     * @param workspaceId 知识归属工作空间 ID（与发布事件 workspaceId 对齐落库）
     * @param knowledgeId 知识 ID
     * @return 摘要文本；无记录或解析失败返回 null
     */
    String findLatestSummary(Long workspaceId, Long knowledgeId);
}
