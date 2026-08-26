package com.calwen.xlumen.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 问答知识缺口实体（ai_question_gap）：未被知识覆盖的问题（应答无引用）与系统建议的追问，
 * 保留 source（ANSWER_UNMATCHED|FOLLOWUP_SUGGESTED）与处理状态（UNHANDLED|HANDLED），供运营补库回访。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Getter
@Setter
@TableName("ai_question_gap")
public class QuestionGapEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 用户 ID。 */
    private Long userId;

    /** 会话 ID（可空）。 */
    private Long conversationId;

    /** 问题文本。 */
    private String question;

    /** 来源：ANSWER_UNMATCHED|FOLLOWUP_SUGGESTED。 */
    private String source;

    /** 状态：UNHANDLED|HANDLED。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}