package com.calwen.xlumen.ai.service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务执行上下文（F-1302）：执行器通过它推进度/推流式块/完成/失败，统一落库与 SSE 广播。
 *
 * @author calwen
 * @date 2026/8/13
 */
public class TaskContext {

    private final AiTaskService aiTaskService;
    private final SseService sseService;
    private final Long taskId;
    private final AtomicLong sequence = new AtomicLong(0);

    public TaskContext(AiTaskService aiTaskService, SseService sseService, Long taskId) {
        this.aiTaskService = aiTaskService;
        this.sseService = sseService;
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }

    /** 发布进度（Redis + SSE progress 事件）。 */
    public void publishProgress(int progress) {
        aiTaskService.publishProgress(taskId, progress);
        sseService.publishProgress(taskId, sequence.incrementAndGet(), progress);
    }

    /** 发布流式内容块（SSE chunk 事件）。 */
    public void publishChunk(String chunk) {
        sseService.publishChunk(taskId, sequence.incrementAndGet(), chunk);
    }

    /** 完成任务（落库 + SSE done 事件）。 */
    public void complete(String resultJson) {
        aiTaskService.complete(taskId, resultJson);
        sseService.publishDone(taskId, sequence.incrementAndGet(), resultJson);
    }

    /** 任务失败（落库 + SSE error 事件）。 */
    public void fail(String msg) {
        aiTaskService.fail(taskId, msg);
        sseService.publishError(taskId, sequence.incrementAndGet(), msg);
    }
}
