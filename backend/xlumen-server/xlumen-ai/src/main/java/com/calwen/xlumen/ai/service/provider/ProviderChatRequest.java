package com.calwen.xlumen.ai.service.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商对话请求：model 由网关按场景配置填充，temperature/maxTokens 可空。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderChatRequest {

    /** 模型名（由网关按场景配置填充）。 */
    private String model;

    /** 消息列表。 */
    private List<ChatMessage> messages;

    /** 采样温度（可空）。 */
    private Double temperature;

    /** 最大生成 token 数（可空）。 */
    private Integer maxTokens;

    /** 是否流式。 */
    private boolean stream;
}
