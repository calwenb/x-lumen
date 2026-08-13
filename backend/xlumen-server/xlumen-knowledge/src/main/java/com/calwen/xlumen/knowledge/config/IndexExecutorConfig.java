package com.calwen.xlumen.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 索引线程池装配（F-0402）：发布即索引异步执行的专用线程池（core2/max4/queue100）。
 * 拒绝策略 CallerRunsPolicy：队列满时由发布线程兜底执行，保证索引任务不丢失。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Configuration
public class IndexExecutorConfig {

    /** 核心线程数。 */
    private static final int CORE_POOL_SIZE = 2;
    /** 最大线程数。 */
    private static final int MAX_POOL_SIZE = 4;
    /** 空闲线程存活时间（秒）。 */
    private static final long KEEP_ALIVE_SECONDS = 60L;
    /** 有界队列容量。 */
    private static final int QUEUE_CAPACITY = 100;

    /**
     * 索引专用线程池（守护线程，命名 knowledge-index-*）。
     *
     * @return ExecutorService
     */
    @Bean(name = "indexExecutor")
    public ExecutorService indexExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "knowledge-index-" + seq.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
