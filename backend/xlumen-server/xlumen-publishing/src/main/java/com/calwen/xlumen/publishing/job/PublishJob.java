package com.calwen.xlumen.publishing.job;

import com.calwen.xlumen.publishing.service.ReleaseService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时发布任务（F-0905）：自主调度（非 Spring Task），每分钟扫描 PENDING 且 publish_at&lt;=now 的发布记录并幂等执行。
 * 用 ScheduledExecutorService 避免与 boot 调度装配耦合；发布逻辑与状态流转集中在 ReleaseService.publishDue。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class PublishJob {

    private static final Logger log = LoggerFactory.getLogger(PublishJob.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "publish-scheduler");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private ReleaseService releaseService;

    /** 启动定时任务：初始延迟 1 分钟，之后每分钟执行一次。 */
    @PostConstruct
    public void start() {
        executor.scheduleWithFixedDelay(this::runOnce, 1, 1, TimeUnit.MINUTES);
        log.info("定时发布任务已启动（每分钟扫描 PENDING 发布记录）");
    }

    /** 单次扫描：异常不外抛，避免中断调度线程。 */
    private void runOnce() {
        try {
            releaseService.publishDue();
        } catch (Exception e) {
            log.warn("定时发布扫描异常", e);
        }
    }

    /** 容器关闭时停止调度线程。 */
    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
