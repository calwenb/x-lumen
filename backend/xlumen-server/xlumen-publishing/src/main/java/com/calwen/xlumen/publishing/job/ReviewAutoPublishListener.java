package com.calwen.xlumen.publishing.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.event.AiTaskCompletedEvent;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import com.calwen.xlumen.publishing.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 发布后异步审核完结监听（IDEA-024）：AI 审校任务结束时驱动「自动审核发布」—
 * 通过→自动发布（立即/定时），未通过/失败→驳回回草稿（F-0907 闸门）。仅处理 auto_mode=1 的记录。
 * 任务在 AI 异步线程执行、无请求上下文：本监听显式建立 WorkspaceContext（try/finally 清理），
 * 异常一律捕获不抛出——不中断同事件的其他监听（如 notification 模块的站内信）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class ReviewAutoPublishListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewAutoPublishListener.class);

    private final ReviewMapper reviewMapper;
    private final ReviewService reviewService;

    public ReviewAutoPublishListener(ReviewMapper reviewMapper, ReviewService reviewService) {
        this.reviewMapper = reviewMapper;
        this.reviewService = reviewService;
    }

    @EventListener
    public void onTaskCompleted(AiTaskCompletedEvent event) {
        if (event == null || event.getTaskId() == null || event.getWorkspaceId() == null
                || event.getUserId() == null || !"REVIEWER".equals(event.getScene())) {
            return;
        }
        ReviewEntity review = reviewMapper.selectOne(Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getAiTaskId, event.getTaskId())
                .last("LIMIT 1"));
        if (review == null || !"PENDING".equals(review.getStatus())
                || !Integer.valueOf(1).equals(review.getAutoMode())) {
            return;
        }
        WorkspaceContext.set(event.getWorkspaceId(), event.getUserId(), "");
        try {
            reviewService.finalizeAutoReview(review.getId(), event.getStatus(),
                    event.getResultJson(), event.getErrorMsg());
        } catch (Exception e) {
            log.warn("AI 审核完成自动发布回调失败 reviewId={} status={}", review.getId(), event.getStatus(), e);
            // 不抛出：审核记录保持 PENDING，作者可在审核中心查看重试（getReview/enrichAutoState 兜底）
        } finally {
            WorkspaceContext.clear();
        }
    }
}
