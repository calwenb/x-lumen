package com.calwen.xlumen.ai.service.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商对话统一结果（IDEA-025 F-0708）：chat() 返回值与 chatStream() 终态。
 * content 为文本内容（无则空串）；toolCalls 为模型发起的工具调用（无则空列表）；
 * finishReason 取值 stop|tool_calls|length|content_filter 等。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderChatResult {

    /** 文本内容（无则空串）。 */
    private String content;

    /** 模型发起的工具调用（无则空列表）。 */
    private List<ToolCall> toolCalls;

    /** 结束原因（stop|tool_calls|length|content_filter 等，可空）。 */
    private String finishReason;

    /** 是否本轮发起工具调用。 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}