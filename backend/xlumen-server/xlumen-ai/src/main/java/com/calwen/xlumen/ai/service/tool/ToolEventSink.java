package com.calwen.xlumen.ai.service.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 工具运行数据收集器（OPT-2/D20 全量迁移）：ChatClient 自动工具循环在 ToolCallback 内执行，
 * 中间轮次的消息框架不对外暴露——由适配层把每次工具调用记为「事件 + 配对」喂给本收集器：
 * 事件（SSE tool start/done 推送，接口友好实时可见）与配对（assistant 工具调用行 + tool 行，供 chat_message 落库）。
 * 同请求内调用序号自增；调用数计数用于预算上限（ToolCallbackAdapter 执行前检查）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolEventSink {

    /** SSE 实时推送（无 SSE 上下文时传 no-op）。 */
    private Consumer<ToolEvent> liveListener = event -> {
    };

    /** 事件轨迹（SSE 顺序）。 */
    private final List<ToolEvent> events = Collections.synchronizedList(new ArrayList<>());

    /** assistant 工具调用 + tool 响应的配对（chat_message 落库顺序）。 */
    private final List<ToolPair> pairs = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger seq = new AtomicInteger();
    private final AtomicInteger callCount = new AtomicInteger();

    /** 取下一个序号并记录事件（先推 SSE 再入轨迹）。 */
    public void record(ToolEvent event) {
        events.add(event);
        liveListener.accept(event);
    }

    /** 记录一个配对（含合成的 tool_call_id）。 */
    public void recordPair(ToolPair pair) {
        pairs.add(pair);
    }

    public int nextSeq() {
        return seq.incrementAndGet();
    }

    public int incrementCalls() {
        return callCount.incrementAndGet();
    }

    public int callCount() {
        return callCount.get();
    }
}