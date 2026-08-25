package com.calwen.xlumen.ai.util;

/**
 * SSE 事件名常量（C 批去重）：AI 模块 SSE 协议统一收口，与前端 ai/utils/sseEvent.ts 对应。
 * 覆盖任务事件（chunk/progress/done/error）与对话事件（tool/citation/done/error）。
 *
 * @author calwen
 * @date 2026/8/25
 */
public final class SseEventName {

    private SseEventName() {
    }

    /** 流式内容增量。 */
    public static final String CHUNK = "chunk";

    /** 任务进度 0-100。 */
    public static final String PROGRESS = "progress";

    /** 流程/任务结束（无 error 时）。 */
    public static final String DONE = "done";

    /** 错误。 */
    public static final String ERROR = "error";

    /** 对话引用证据（Q：F-0405 引用溯源）。 */
    public static final String CITATION = "citation";

    /** 工具调用过程事件（Q：IDEA-025 工具轨迹）。 */
    public static final String TOOL = "tool";
}
