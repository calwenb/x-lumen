package com.calwen.xlumen.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 对话长期记忆实体（chat_memory）：按工作空间+用户维度保存跨会话记忆摘要，
 * memory_json 为 JSON 数组文本（["YYYY-MM-DD HH:mm 主题：要点", ...]，上限 20 条）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Getter
@Setter
@TableName("chat_memory")
public class ChatMemoryEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 用户 ID（记忆仅登录用户，访客跳过）。 */
    private Long userId;

    /** 记忆条目 JSON 数组文本。 */
    private String memoryJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}