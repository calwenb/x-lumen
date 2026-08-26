package com.calwen.xlumen.ai.service;

/**
 * AI 对话长期记忆服务：跨会话记忆的读取与增量写回（按工作空间+用户维度）。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface ChatMemoryService {

    /**
     * 读取用户记忆（按行拼接的文本，供 System Prompt 附加）。
     *
     * @param workspaceId 工作空间 ID
     * @param userId      用户 ID（为空不查）
     * @return 记忆文本或 null（无记忆时）
     */
    String loadMemory(Long workspaceId, Long userId);

    /**
     * 追加一条记忆（主题-要点），超出上限丢弃最旧；异常吞掉不抛。
     *
     * @param workspaceId 工作空间 ID（为空跳过）
     * @param userId      用户 ID（为空跳过）
     * @param query       本次问题（主题来源）
     * @param answer      本次回答（要点来源，取前 200 字）
     */
    void appendMemory(Long workspaceId, Long userId, String query, String answer);
}