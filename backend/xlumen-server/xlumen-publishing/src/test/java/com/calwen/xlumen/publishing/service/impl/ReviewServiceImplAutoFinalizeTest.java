package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import com.calwen.xlumen.publishing.service.ReleaseService;
import com.calwen.xlumen.ai.api.AiApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布后异步审核最终化单测：COMPLETED 无 error → 通过并自动发布（携带 autoPublishAt）；
 * COMPLETED 含 error → 驳回回草稿；FAILED → 驳回；非自动模式不动。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ReviewServiceImplAutoFinalizeTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private ContentApi contentApi;

    @Mock
    private KnowledgeApi knowledgeApi;

    @Mock
    private AiApi aiApi;

    @Mock
    private WorkspaceApi workspaceApi;

    @Mock
    private ReleaseService releaseService;

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        WorkspaceContext.set(10L, 100L, "tester");
        when(contentApi.getEditorKnowledge(10L, 1L)).thenReturn(EditorKnowledgeDTO.builder()
                .id(1L).workspaceId(10L).title("测试").content("正文").kbId(5L)
                .directoryId(0L).status(3).version(2L).build());
        when(contentApi.publishKnowledge(eq(10L), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    private ReviewEntity autoReview() {
        ReviewEntity r = new ReviewEntity();
        r.setId(11L);
        r.setWorkspaceId(10L);
        r.setKnowledgeId(1L);
        r.setKnowledgeTitle("测试");
        r.setVersion(2L);
        r.setReviewerId(100L);
        r.setAiTaskId(7L);
        r.setStatus("PENDING");
        r.setAutoMode(1);
        r.setAutoPublishAt(LocalDateTime.of(2026, 8, 25, 10, 0));
        return r;
    }

    @Test
    void completed_withoutError_approvesAndAutoPublishesWithScheduledAt() {
        when(reviewMapper.selectOne(any())).thenReturn(autoReview());

        reviewService.finalizeAutoReview(11L, "COMPLETED",
                "[{\"severity\":\"info\",\"position\":\"L1\",\"evidence\":\"e\",\"suggestion\":\"s\"}]", null);

        verify(reviewMapper).updateById(any(com.calwen.xlumen.publishing.entity.ReviewEntity.class));
        // 知识迁移 APPROVED + 自动发布携带 autoPublishAt
        verify(contentApi).publishKnowledge(eq(10L), any());
        ArgumentCaptor<CreateReleaseDTO> captor = ArgumentCaptor.forClass(CreateReleaseDTO.class);
        verify(releaseService).release(captor.capture());
        assertThat(captor.getValue().getKnowledgeId()).isEqualTo(1L);
        assertThat(captor.getValue().getVersion()).isEqualTo(2L);
        assertThat(captor.getValue().getPublishAt()).isEqualTo(LocalDateTime.of(2026, 8, 25, 10, 0));
    }

    @Test
    void completed_withError_blocksAndRevertsToDraft() {
        when(reviewMapper.selectOne(any())).thenReturn(autoReview());

        reviewService.finalizeAutoReview(11L, "COMPLETED",
                "[{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"e\",\"suggestion\":\"s\"}]", null);

        verify(releaseService, never()).release(any());
        ArgumentCaptor<KnowledgePublishDTO> captor = ArgumentCaptor.forClass(KnowledgePublishDTO.class);
        verify(contentApi).publishKnowledge(eq(10L), captor.capture());
        // 回草稿（目标 2=DRAFT）
        assertThat(captor.getValue().getTargetStatus()).isEqualTo(2);
    }

    @Test
    void failed_rejectsAndRevertsToDraft() {
        when(reviewMapper.selectOne(any())).thenReturn(autoReview());

        reviewService.finalizeAutoReview(11L, "FAILED", null, "供应商不可用");

        verify(releaseService, never()).release(any());
        ArgumentCaptor<KnowledgePublishDTO> captor = ArgumentCaptor.forClass(KnowledgePublishDTO.class);
        verify(contentApi).publishKnowledge(eq(10L), captor.capture());
        assertThat(captor.getValue().getTargetStatus()).isEqualTo(2);
    }

    @Test
    void nonAutoMode_orNonPending_noop() {
        ReviewEntity manual = autoReview();
        manual.setAutoMode(0);
        when(reviewMapper.selectOne(any())).thenReturn(manual);

        reviewService.finalizeAutoReview(11L, "COMPLETED", "[]", null);

        verify(reviewMapper, never()).updateById(any(com.calwen.xlumen.publishing.entity.ReviewEntity.class));
        verify(releaseService, never()).release(any());
    }
}