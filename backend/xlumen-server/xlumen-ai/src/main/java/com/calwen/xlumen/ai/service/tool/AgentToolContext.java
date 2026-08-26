package com.calwen.xlumen.ai.service.tool;

import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Consumer;

/**
 * 工具运行业务上下文：按 WorkspaceContext 身份 + resolveVisibleKbIds 过滤（决策 D13），
 * 访客 userId=null 自动收敛到公开库；模型参数中的 kbId 必须校验在可见集合内，越权返回错误信封而非执行。
 * 命名避开 Spring AI 的 {@link org.springframework.ai.chat.model.ToolContext}（D20 消除同名遮蔽）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolContext {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 用户 ID（访客为 null，工具据此走公开库推导）。 */
    private Long userId;

    /** 会话 ID（可空）。 */
    private Long conversationId;

    /** 会话锁定的知识库，可空）。 */
    private Long kbId;

    /** ChatRuntime 注入的引用证据收集器：knowledge.search 命中结果同时上报，聚合进 citation 事件。 */
    private Consumer<List<SearchResultDTO>> citationCollector;
}
