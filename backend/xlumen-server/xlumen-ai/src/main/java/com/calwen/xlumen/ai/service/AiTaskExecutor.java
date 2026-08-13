package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;

/**
 * AI 任务执行器（F-1302）：按场景注册，执行器内部通过 TaskContext 推进度/结果。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface AiTaskExecutor {

    /**
     * 执行器负责的场景。
     *
     * @return 场景
     */
    AiScene scene();

    /**
     * 执行任务：流式/非流式完成后调用 ctx.complete 或 ctx.fail。
     *
     * @param task 任务实体
     * @param ctx  任务上下文
     */
    void execute(AiTaskEntity task, TaskContext ctx);
}
