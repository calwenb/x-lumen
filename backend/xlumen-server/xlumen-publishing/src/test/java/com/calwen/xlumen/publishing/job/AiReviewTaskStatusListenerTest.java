package com.calwen.xlumen.publishing.job;

import com.calwen.xlumen.common.event.AiTaskCompletedEvent;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 审校任务状态镜像监听单测：COMPLETED 写状态+结果快照、FAILED 写状态+失败原因、
 * 非 REVIEWER 场景不查库、无匹配记录不抛异常；已存在快照不被覆盖（幂等）。
 *
 * @author calwen
 * @date 2026/8/26
 */
class AiReviewTaskStatusListenerTest {

    @Mock
    private ReviewMapper reviewMapper;

    private AiReviewTaskStatusListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new AiReviewTaskStatusListener(reviewMapper);
    }

    private ReviewEntity review(Long taskId) {
        ReviewEntity r = new ReviewEntity();
        r.setId(1L);
        r.setWorkspaceId(10L);
        r.setAiTaskId(taskId);
        return r;
    }

    private AiTaskCompletedEvent event(long taskId, String scene, String status, String resultJson, String errorMsg) {
        return AiTaskCompletedEvent.builder()
                .taskId(taskId).workspaceId(10L).userId(100L).scene(scene)
                .status(status).resultJson(resultJson).errorMsg(errorMsg).inputJson("{}")
                .build();
    }

    @Test
    void completedEvent_matchingReview_backfillsStatusAndResultJson() {
        when(reviewMapper.selectOne(any())).thenReturn(review(7L));

        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED", "[{\"severity\":\"info\"}]", null));

        ArgumentCaptor<ReviewEntity> captor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(reviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAiStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getAiResultJson()).isEqualTo("[{\"severity\":\"info\"}]");
        assertThat(captor.getValue().getAiError()).isNull();
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void failedEvent_matchingReview_backfillsStatusAndError() {
        when(reviewMapper.selectOne(any())).thenReturn(review(7L));

        listener.onTaskCompleted(event(7L, "REVIEWER", "FAILED", null, "供应商不可用"));

        ArgumentCaptor<ReviewEntity> captor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(reviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAiStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getAiError()).isEqualTo("供应商不可用");
        assertThat(captor.getValue().getAiResultJson()).isNull();
    }

    @Test
    void completedEvent_withExistingSnapshot_keepsSnapshot() {
        ReviewEntity r = review(7L);
        r.setAiResultJson("[{\"severity\":\"error\"}]");
        when(reviewMapper.selectOne(any())).thenReturn(r);

        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED", "[{\"severity\":\"info\"}]", null));

        ArgumentCaptor<ReviewEntity> captor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(reviewMapper).updateById(captor.capture());
        // 快照已存在则不被事件结果覆盖（幂等）
        assertThat(captor.getValue().getAiStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getAiResultJson()).isEqualTo("[{\"severity\":\"error\"}]");
    }

    @Test
    void nonReviewerScene_doesNotQueryDb() {
        listener.onTaskCompleted(event(7L, "WRITING", "COMPLETED", "[]", null));

        verify(reviewMapper, never()).selectOne(any());
        verify(reviewMapper, never()).updateById(any(ReviewEntity.class));
    }

    @Test
    void noMatchingReview_doesNotThrow() {
        when(reviewMapper.selectOne(any())).thenReturn(null);

        // 无匹配审核记录直接返回，不抛异常
        listener.onTaskCompleted(event(7L, "REVIEWER", "COMPLETED", "[]", null));

        verify(reviewMapper, never()).updateById(any(ReviewEntity.class));
    }
}