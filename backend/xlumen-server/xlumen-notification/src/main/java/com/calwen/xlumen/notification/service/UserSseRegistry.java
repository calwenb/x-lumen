package com.calwen.xlumen.notification.service;

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
 * 用户级通知 SSE 注册表（IDEA-024）：站内消息实时推送通道——通知创建时按接收用户推送
 * 「notification」事件，前端收到后右上角 ElNotification 弹窗。单向服务端推送用 SSE 即可，
 * 无需 WebSocket（项目已复用 fetch 流式解析工具）。断线期间消息由前端 30s 轮询兜底。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Service
public class UserSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(UserSseRegistry.class);

    /** 连接超时 30 分钟（与任务 SSE 一致）。 */
    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /** 心跳间隔 30s（防代理/容器空闲断开）。 */
    private static final long HEARTBEAT_SECONDS = 30;

    private final Map<Long, Set<SseEmitter>> connections = new ConcurrentHashMap<>();
    private final Map<SseEmitter, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "noti-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    /** 订阅某用户的通知流（登录态）；返回 SseEmitter，注册心跳与清理回调。 */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        connections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> unsubscribe(userId, emitter));
        emitter.onTimeout(() -> unsubscribe(userId, emitter));
        emitter.onError(e -> unsubscribe(userId, emitter));
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                unsubscribe(userId, emitter);
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        heartbeats.put(emitter, heartbeat);
        return emitter;
    }

    /** 向用户推送一条通知（fire-and-forget：无活跃连接/发送失败均忽略，轮询兜底）。 */
    public void push(Long userId, String eventName, Object payload) {
        if (userId == null) {
            return;
        }
        Set<SseEmitter> set = connections.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(JSONUtil.toJsonStr(payload)));
            } catch (Exception e) {
                log.debug("通知 SSE 推送失败 userId={}", userId, e);
            }
        }
    }

    private void unsubscribe(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = connections.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                connections.remove(userId, set);
            }
        }
        ScheduledFuture<?> heartbeat = heartbeats.remove(emitter);
        if (heartbeat != null) {
            heartbeat.cancel(true);
        }
        try {
            emitter.complete();
        } catch (Exception ignore) {
            // 连接已关闭
        }
    }
}