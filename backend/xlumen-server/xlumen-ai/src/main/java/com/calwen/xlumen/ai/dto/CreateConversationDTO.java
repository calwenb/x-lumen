package com.calwen.xlumen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建 AI 会话入参（F-0701）：标题必填。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationDTO {

    /** 会话标题。 */
    private String title;
}
