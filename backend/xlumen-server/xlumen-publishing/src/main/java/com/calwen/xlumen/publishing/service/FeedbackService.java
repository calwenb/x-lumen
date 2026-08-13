package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.publishing.dto.CreateFeedbackDTO;
import com.calwen.xlumen.publishing.vo.FeedbackVO;

/**
 * 读者纠错服务（F-1001）：匿名可提交，同一 IP 60s 限流 2 次，返回追踪号。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface FeedbackService {

    /**
     * 提交读者纠错（F-1001）：校验文章存在 + IP 限流（Redis），生成追踪号。
     *
     * @param articleId 文章 ID
     * @param dto       纠错内容（含服务端回填的 ip）
     * @return 纠错视图（含追踪号）
     */
    FeedbackVO createFeedback(Long articleId, CreateFeedbackDTO dto);
}
