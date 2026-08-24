package com.calwen.xlumen.notification.event;

import com.calwen.xlumen.common.event.AiTaskCompletedEvent;
import com.calwen.xlumen.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 审核完成通知监听单测（IDEA-024）：REVIEWER 完结按 severity 汇总生成「通过/未通过/失败」消息，
 * 非 REVIEWER 场景不打扰；消息链接指向审核中心。
 *
 * @author calwen
 * @date 2026/8/24
 */
class AiTaskCompletedListenerTest {

    @Mock
    private NotificationService notificationService;

    private AiTaskCompletedListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new AiTaskCompletedListener(notificationService);
    }

    private AiTaskCompletedEvent event(String status, String resultJson) {
        return AiTaskCompletedEvent.builder()
                .taskId(1L).workspaceId(10L).userId(100L).scene("REVIEWER")
                .status(status).resultJson(resultJson).inputJson("{\"title\":\"部署指南\",\"knowledgeId\":\"2089895161090592769\"}")
                .build();
    }

    @Test
    void completed_withError_notifiesBlocked() {
        listener.onTaskCompleted(event("COMPLETED",
                "[{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"e\",\"suggestion\":\"s\"},"
                        + "{\"severity\":\"warning\",\"position\":\"L2\",\"evidence\":\"e\",\"suggestion\":\"s\"}]"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("REVIEW"), title.capture(), content.capture(), link.capture());
        assertThat(title.getValue()).isEqualTo("AI 审核未通过");
        assertThat(content.getValue()).contains("部署指南").contains("1 条高危问题");
        assertThat(link.getValue()).isEqualTo("/studio/review");
    }

    @Test
    void completed_withoutError_notifiesPassed() {
        listener.onTaskCompleted(event("COMPLETED",
                "[{\"severity\":\"info\",\"position\":\"L1\",\"evidence\":\"e\",\"suggestion\":\"s\"}]"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("REVIEW"), title.capture(), content.capture(),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(title.getValue()).isEqualTo("AI 审核通过");
        assertThat(content.getValue()).contains("已通过 AI 审核").contains("1 条优化建议");
    }

    @Test
    void failed_notifiesRetry() {
        listener.onTaskCompleted(event("FAILED", null));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("REVIEW"), title.capture(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(title.getValue()).isEqualTo("AI 审核失败");
    }

    @Test
    void nonReviewerScene_ignored() {
        listener.onTaskCompleted(AiTaskCompletedEvent.builder()
                .taskId(1L).workspaceId(10L).userId(100L).scene("WRITING").status("COMPLETED").build());
        verify(notificationService, never()).create(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void guestUser_ignored() {
        listener.onTaskCompleted(AiTaskCompletedEvent.builder()
                .taskId(1L).workspaceId(10L).userId(null).scene("REVIEWER").status("COMPLETED").build());
        verify(notificationService, never()).create(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}