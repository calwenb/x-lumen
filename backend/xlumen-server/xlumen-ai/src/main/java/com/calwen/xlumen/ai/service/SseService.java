package com.calwen.xlumen.ai.service;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 任务 SSE 服务（F-1302）：封装事件 chunk/progress/done/error，data 带 taskId/sequence/内容，30s 心跳。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    /** 订阅超时 30 分钟。 */
    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /** 心跳间隔 30s。 */
    private static final long HEARTBEAT_SECONDS = 30;

    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<SseEmitter, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /**
     * 订阅任务事件：返回 SseEmitter，注册心跳与清理回调。
     *
     * @param taskId 任务 ID
     * @return SseEmitter
     */
    public SseEmitter subscribe(Long taskId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        subscribers.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> unsubscribe(taskId, emitter));
        emitter.onTimeout(() -> unsubscribe(taskId, emitter));
        emitter.onError(e -> unsubscribe(taskId, emitter));
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                unsubscribe(taskId, emitter);
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        heartbeats.put(emitter, heartbeat);
        return emitter;
    }

    /** 发布 chunk 事件。 */
    public void publishChunk(Long taskId, long sequence, String content) {
        publish(taskId, "chunk", JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("taskId", String.valueOf(taskId))
                .set("sequence", sequence)
                .set("content", content)));
    }

    /** 发布 progress 事件。 */
    public void publishProgress(Long taskId, long sequence, int progress) {
        publish(taskId, "progress", JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("taskId", String.valueOf(taskId))
                .set("sequence", sequence)
                .set("progress", progress)));
    }

    /** 发布 done 事件。 */
    public void publishDone(Long taskId, long sequence, String resultJson) {
        publish(taskId, "done", JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("taskId", String.valueOf(taskId))
                .set("sequence", sequence)
                .set("resultJson", resultJson)));
    }

    /** 发布 error 事件。 */
    public void publishError(Long taskId, long sequence, String message) {
        publish(taskId, "error", JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("taskId", String.valueOf(taskId))
                .set("sequence", sequence)
                .set("message", message)));
    }

    private void publish(Long taskId, String event, String data) {
        Set<SseEmitter> set = subscribers.get(taskId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                unsubscribe(taskId, emitter);
            }
        }
    }

    private void unsubscribe(Long taskId, SseEmitter emitter) {
        ScheduledFuture<?> heartbeat = heartbeats.remove(emitter);
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        Set<SseEmitter> set = subscribers.get(taskId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                subscribers.remove(taskId);
            }
        }
        try {
            emitter.complete();
        } catch (Exception ignore) {
            log.debug("关闭 SseEmitter 失败", ignore);
        }
    }
}
