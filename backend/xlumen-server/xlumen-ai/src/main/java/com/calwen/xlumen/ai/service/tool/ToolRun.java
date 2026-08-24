package com.calwen.xlumen.ai.service.tool;

import com.calwen.xlumen.ai.enums.AiScene;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次工具化调用的运行上下文（OPT-2/D20 全量迁移）：ChatRuntime 的 chatWithTools 系列入口参数。
 * toolContext 为业务工具上下文（工作空间/身份/会话锁定库/引用收集器），
 * sink 收集 SSE 事件与落库配对；场景决定预算上限（REVIEWER 用审校轮数上限，其余用全局调用数上限）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolRun {

    /** 场景（决定工具集与预算上限）。 */
    private AiScene scene;

    /** 业务工具上下文（原 AgentRunner 注入口径）。 */
    private AgentToolContext toolContext;

    /** 事件与配对收集器（SSE 实时推送 + 落库）。 */
    private ToolEventSink sink;
}