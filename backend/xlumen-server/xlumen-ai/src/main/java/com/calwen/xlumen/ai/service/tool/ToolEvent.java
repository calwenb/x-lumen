package com.calwen.xlumen.ai.service.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具过程事件（迁移后原样保留）：SSE tool 事件的数据体与轨迹记录共用同一结构。
 * start：phase=start + argsSummary；done：phase=done + ok/durationMs/summary。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolEvent {

    /** 本次请求内的工具调用序号（从 1 开始）。 */
    private int seq;

    /** 工具名。 */
    private String name;

    /** 阶段：start|done。 */
    private String phase;

    /** 参数摘要（start 时携带，如 query=部署）。 */
    private String argsSummary;

    /** 是否成功（done 时携带）。 */
    private Boolean ok;

    /** 执行耗时毫秒（done 时携带）。 */
    private Long durationMs;

    /** 结果摘要（done 时携带，如 命中 5 条）。 */
    private String summary;
}