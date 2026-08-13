package com.calwen.xlumen.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体（chat_conversation，F-0701）：多轮对话容器，标题取首条提问截断。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("chat_conversation")
public class ChatConversationEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 用户 ID（访客会话为 NULL）。 */
    private Long userId;

    /** 会话标题（首条提问截断）。 */
    private String title;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
