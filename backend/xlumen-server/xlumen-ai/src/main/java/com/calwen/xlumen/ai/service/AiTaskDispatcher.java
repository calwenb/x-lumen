package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.enums.AiTaskStatus;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * AI 任务派发器（F-1302）：注册执行器 Map&lt;AiScene, AiTaskExecutor&gt;，submit 后提交线程池执行；
 * 拒绝时任务保持 QUEUED。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class AiTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AiTaskDispatcher.class);

    private final Map<AiScene, AiTaskExecutor> executors = new EnumMap<>(AiScene.class);
    private final AiTaskService aiTaskService;
    private final SseService sseService;
    private final ThreadPoolTaskExecutor taskExecutor;

    public AiTaskDispatcher(List<AiTaskExecutor> executorList,
                            AiTaskService aiTaskService,
                            SseService sseService,
                            @Qualifier("aiTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        for (AiTaskExecutor executor : executorList) {
            executors.put(executor.scene(), executor);
        }
        this.aiTaskService = aiTaskService;
        this.sseService = sseService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 派发任务到线程池；线程池满拒绝时保持 QUEUED（不标 RUNNING）。
     *
     * @param task 任务实体
     */
    public void dispatch(AiTaskEntity task) {
        AiTaskExecutor executor = executors.get(AiScene.valueOf(task.getScene()));
        if (executor == null) {
            log.warn("未找到场景执行器 scene={} taskId={}", task.getScene(), task.getId());
            aiTaskService.fail(task.getId(), "该场景暂不支持");
            return;
        }
        try {
            taskExecutor.execute(() -> run(executor, task));
        } catch (RejectedExecutionException e) {
            log.warn("任务线程池已满，任务保持 QUEUED taskId={}", task.getId());
        }
    }

    private void run(AiTaskExecutor executor, AiTaskEntity task) {
        aiTaskService.markRunning(task.getId());
        TaskContext ctx = new TaskContext(aiTaskService, sseService, task.getId());
        try {
            executor.execute(task, ctx);
        } catch (Exception e) {
            log.error("任务执行异常 taskId={}", task.getId(), e);
            AiTaskEntity current = aiTaskService.get(task.getWorkspaceId(), task.getId());
            if (current != null && !AiTaskStatus.valueOf(current.getStatus()).isTerminal()) {
                ctx.fail("执行异常：" + safeMessage(e));
            }
        }
    }

    private String safeMessage(Throwable e) {
        String msg = e.getMessage();
        if (StrUtil.isBlank(msg)) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
