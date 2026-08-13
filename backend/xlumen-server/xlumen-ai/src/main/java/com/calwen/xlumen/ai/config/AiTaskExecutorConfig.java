package com.calwen.xlumen.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 任务线程池装配（F-1302）：任务执行 core2/max4/queue200，拒绝时抛异常使任务保持 QUEUED；
 * 对话流式独立线程池 core2/max8/queue50，避免阻塞 AI 任务执行。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Configuration
public class AiTaskExecutorConfig {

    /** AI 任务执行线程池：拒绝策略 AbortPolicy（TaskRejectedException），任务保持 QUEUED。 */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 对话流式线程池：CallerRunsPolicy 保证提交不丢失（SSE 场景）。 */
    @Bean("chatStreamExecutor")
    public ThreadPoolTaskExecutor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
