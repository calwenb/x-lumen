package com.calwen.xlumen.ai.service;

import java.util.List;

/**
 * 相关追问生成服务：基于问题与回答摘要轻量生成 3 个相关追问。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface FollowupGenerator {

    /**
     * 生成相关追问（最多 3 条）；生成本身失败返回空列表（不抛出）。
     *
     * @param workspaceId    工作空间 ID
     * @param question       用户问题
     * @param answerSummary  回答摘要
     * @return 追问列表（可为空）
     */
    List<String> generate(Long workspaceId, String question, String answerSummary);

    /**
     * 解析生成文本：按行取前 3 条非空行。
     *
     * @param raw 生成原文
     * @return 追问列表（可为空）
     */
    List<String> parse(String raw);
}