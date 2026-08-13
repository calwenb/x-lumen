package com.calwen.xlumen.ai.enums;

/**
 * AI 任务状态机（F-1302，BACKEND.md §14）：
 * QUEUED → RUNNING → COMPLETED；失败分支 FAILED，人工可 CANCELLED；
 * WAITING_APPROVAL 为 V2 大纲可选确认（F-0602）预留。
 * 任务事实存 MySQL（决策 D6），进度存 Redis 短期状态。
 *
 * @author calwen
 * @date 2026/8/13
 */
public enum AiTaskStatus {

    /** 排队中：已落库等待执行。 */
    QUEUED,
    /** 执行中：执行器处理中。 */
    RUNNING,
    /** 等待人工确认（V2 大纲确认 F-0602 预留，MVP 不启用）。 */
    WAITING_APPROVAL,
    /** 已完成：结果写入 result_json。 */
    COMPLETED,
    /** 失败：error_msg 记录对外脱敏原因，支持有限重试。 */
    FAILED,
    /** 已取消：人工取消。 */
    CANCELLED;

    /** 终止状态（不再发生流转）。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
