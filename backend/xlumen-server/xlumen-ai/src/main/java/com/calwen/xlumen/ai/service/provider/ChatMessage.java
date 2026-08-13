package com.calwen.xlumen.ai.service.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商对话消息：role 为 system|user|assistant。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 角色：system|user|assistant。 */
    private String role;

    /** 消息内容。 */
    private String content;
}
