package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.vo.QuestionGapPageVO;

import java.util.List;

/**
 * AI 问答知识缺口服务：未命中引用的提问与系统建议的追问落库，供运营补库；
 * 分页查询与置为已处理。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface QuestionGapService {

    /** 来源：应答无引用（知识未覆盖）。 */
    String SOURCE_ANSWER_UNMATCHED = "ANSWER_UNMATCHED";

    /** 来源：系统建议的相关追问。 */
    String SOURCE_FOLLOWUP_SUGGESTED = "FOLLOWUP_SUGGESTED";

    /** 状态：未处理。 */
    String STATUS_UNHANDLED = "UNHANDLED";

    /** 状态：已处理。 */
    String STATUS_HANDLED = "HANDLED";

    /**
     * 记录应答无引用的问题（用户为空跳过）。
     */
    void recordUnmatched(Long workspaceId, Long userId, Long conversationId, String question);

    /**
     * 记录系统建议的追问（用户为空或列表为空跳过）。
     */
    void recordFollowups(Long workspaceId, Long userId, Long conversationId, List<String> followups);

    /**
     * 分页查询（时间倒序，可按状态过滤）。
     */
    QuestionGapPageVO page(Long workspaceId, String status, long pageNo, long pageSize);

    /**
     * 置为已处理（归属校验工作空间一致）。
     */
    void markHandled(Long workspaceId, Long id);
}