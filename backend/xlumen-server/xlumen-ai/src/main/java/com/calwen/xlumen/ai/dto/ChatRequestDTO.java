package com.calwen.xlumen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 对话入参：query 为提问内容，conversationId 为空表示新会话。
 * KB-3 检索范围参数：kbId 限定单库；allVisible 控制是否检索全部可见库（决策 D13，
 * 可见库集合由后端按身份推导）；knowledgeIds 为多篇对比限定（单篇精确过滤，多篇保持可见库检索）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO {

    /** 提问内容。 */
    private String query;

    /** 会话 ID（空表示新建会话）。 */
    private Long conversationId;

    /** 限定检索的知识库 ID（可空；空=按 allVisible 决定范围）。 */
    private Long kbId;

    /** 是否检索全部可见库（可空=true；false 且未指定 kbId 时不做知识检索）。 */
    private Boolean allVisible;

    /** 限定对比的知识 ID 列表（可空；单篇时检索精确限定该篇，多篇时保持可见库检索由前端表达对比语义）。 */
    private List<Long> knowledgeIds;
}
