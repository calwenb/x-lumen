package com.calwen.xlumen.publishing.job;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.event.AiTaskCompletedEvent;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import com.calwen.xlumen.publishing.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布后异步审核监听单测：仅对 auto_mode=1 且 PENDING 的 REVIEWER 任务驱动
 * finalizeAutoReview；非自动模式/未知任务/非审核场景不动作；回调异常不抛出（不中断同事件其他监听）。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ReviewAutoPublishListenerTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private ReviewService reviewService;

    private ReviewAutoPublishListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new ReviewAutoPublishListener(reviewMapper, reviewService);
    }

    private ReviewEntity review(Long taskId, String status) {
        ReviewEntity r = new ReviewEntity();
        r.setId(1L);
        r.setWorkspaceId(10L);
        r.setAiTaskId(taskId);
        r.setStatus(status);
        r.setAutoMode(1);
        return r;
    }

    private AiTaskCompletedEvent event(long taskId, String scene, String status) {
        return AiTaskCompletedEvent.builder()
                .taskId(taskId).workspaceId(10L).userId(100L).scene(scene)
                .status(status).resultJson("[]").inputJson("{}")
                .build();
    }

    @Test
    void completed_autoModePending_forwardsToFinalize() {
        when(reviewMapper.selectOne(any())).thenReturn(review(7L, "PENDING"));

        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED"));

        verify(reviewService).finalizeAutoReview(1L, "COMPLETED", "[]", null);
        // 上下文已清理，防止线程复用串号
        org.junit.jupiter.api.Assertions.assertNull(WorkspaceContext.workspaceId());
    }

    @Test
    void nonAutoMode_ignored() {
        ReviewEntity manual = review(7L, "PENDING");
        manual.setAutoMode(0);
        when(reviewMapper.selectOne(any())).thenReturn(manual);

        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED"));

        verify(reviewService, never()).finalizeAutoReview(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void nonPending_ignored() {
        when(reviewMapper.selectOne(any())).thenReturn(review(7L, "APPROVED"));

        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED"));

        verify(reviewService, never()).finalizeAutoReview(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownTask_ignored() {
        when(reviewMapper.selectOne(any())).thenReturn(null);

        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED"));

        verify(reviewService, never()).finalizeAutoReview(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void nonReviewerScene_ignored() {
        listener.onTaskCompleted(event(7L, "WRITING", "COMPLETED"));
        verify(reviewService, never()).finalizeAutoReview(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void callbackThrows_doesNotPropagate() {
        when(reviewMapper.selectOne(any())).thenReturn(review(7L, "PENDING"));
        org.mockito.Mockito.doThrow(new RuntimeException("模拟失败"))
                .when(reviewService).finalizeAutoReview(1L, "COMPLETED", "[]", null);

        // 不抛异常（同事件的 notification 监听不受影响）
        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED"));
        org.junit.jupiter.api.Assertions.assertNull(WorkspaceContext.workspaceId());
    }
}