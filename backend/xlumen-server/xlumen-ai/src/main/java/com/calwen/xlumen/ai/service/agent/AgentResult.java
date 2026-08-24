package com.calwen.xlumen.ai.service.agent;

import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 运行结果（IDEA-025 F-0708）：content 为累积全文（SSE 已流过的内容）；
 * auxMessages 为中间轮轨迹（assistant 工具调用行 + tool 行），调用方按序落库；
 * citations 为聚合证据（knowledge.search 命中去重）；error 非空=执行中出现模型调用错误。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    /** 最终全文（累积所有轮内容增量）。 */
    private String content;

    /** 工具轨迹（done 事件列表）。 */
    private List<ToolEvent> toolTrace;

    /** 聚合证据（去重，含最终 assistant 行引用）。 */
    private List<SearchResultDTO> citations;

    /** 中间轮轨迹消息（assistant+tool，最终 assistant 行由调用方自行落库）。 */
    private List<ChatMessage> auxMessages;

    /** 模型调用错误（非空=执行失败，调用方走 error 通道）。 */
    private String error;
}