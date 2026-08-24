package com.calwen.xlumen.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体（chat_message，F-0701/F-0702）：role=USER|ASSISTANT|TOOL。
 * citations_json 为 JSON 列，经 JacksonTypeHandler 映射 String（引用证据快照）。
 * IDEA-025 F-0708：assistant 行 tool_calls_json 存 ToolCall 数组快照；tool 行 tool_call_id/tool_name 回引。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessageEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID（逻辑外键 chat_conversation.id）。 */
    private Long conversationId;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 用户 ID（访客消息为 NULL）。 */
    private Long userId;

    /** 角色：USER|ASSISTANT|TOOL。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 引用证据（SearchResultDTO 数组快照，JSON 文本，可空）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String citationsJson;

    /** 工具调用记录（ToolCall 数组快照，JSON 文本，assistant 消息携带，可空）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String toolCallsJson;

    /** 工具调用 ID（tool 角色消息携带，回引 assistant 的 tool_calls）。 */
    private String toolCallId;

    /** 工具名（tool 角色消息携带，渲染免 JSON 关联）。 */
    private String toolName;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
