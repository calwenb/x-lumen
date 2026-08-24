package com.calwen.xlumen.ai.service.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商对话消息：role 为 system|user|assistant|tool。
 * assistant 消息可携带 toolCalls（本轮发起的工具调用）；tool 角色消息携带 toolCallId 回引（+name 展示）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 角色：system|user|assistant|tool。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 工具调用（assistant 消息携带）。 */
    private List<ToolCall> toolCalls;

    /** 工具调用 ID（tool 角色消息携带，回引 assistant 的 toolCalls）。 */
    private String toolCallId;

    /** 工具名（tool 角色消息携带，渲染免 JSON 关联）。 */
    private String name;
}