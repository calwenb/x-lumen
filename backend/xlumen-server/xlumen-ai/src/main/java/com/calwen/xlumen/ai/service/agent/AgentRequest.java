package com.calwen.xlumen.ai.service.agent;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 运行请求（IDEA-025 F-0708）：身份/会话上下文 + 初始消息（含 system 提示词）。
 * kbId 为会话锁定的知识库（F-0702 单篇问答场景，可空）；stream 为流式开关
 * （对话走 chatStream，审校/SEO 走 chat——轻量模式是循环的自然退化）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 用户 ID（访客为 null）。 */
    private Long userId;

    /** 会话 ID（可空）。 */
    private Long conversationId;

    /** 会话锁定的知识库（可空）。 */
    private Long kbId;

    /** 初始消息列表（含 system 提示词与用户首问）。 */
    private List<ChatMessage> messages;

    /** 场景（QA/REVIEWER 等）。 */
    private AiScene scene;

    /** 流式开关。 */
    private boolean stream;

    /** 采样温度（可空）。 */
    private Double temperature;

    /** 最大生成 token 数（可空）。 */
    private Integer maxTokens;
}