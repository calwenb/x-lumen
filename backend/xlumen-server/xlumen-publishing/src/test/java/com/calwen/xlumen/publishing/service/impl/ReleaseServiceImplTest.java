package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.event.KnowledgePublishedEvent;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.entity.ReleaseEntity;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReleaseMapper;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import com.calwen.xlumen.publishing.service.HotKnowledgeCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布服务单测（F-0904/F-0905，BUG 修复 2026-08-24）：发布门禁同时认人工审核（审核中心通过，
 * 无 AI 任务）与 AI 审核；已发布版本重复点「发布」走幂等返回不报 409。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ReleaseServiceImplTest {

    @Mock
    private ReleaseMapper releaseMapper;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private ContentApi contentApi;

    @Mock
    private KnowledgeApi knowledgeApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private HotKnowledgeCacheService hotKnowledgeCacheService;

    private ReleaseServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        WorkspaceContext.set(10L, 100L, "tester");
        service = new ReleaseServiceImpl();
        ReflectionTestUtils.setField(service, "releaseMapper", releaseMapper);
        ReflectionTestUtils.setField(service, "reviewMapper", reviewMapper);
        ReflectionTestUtils.setField(service, "contentApi", contentApi);
        ReflectionTestUtils.setField(service, "knowledgeApi", knowledgeApi);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(service, "hotKnowledgeCacheService", hotKnowledgeCacheService);
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    private EditorKnowledgeDTO approvedKnowledge() {
        return EditorKnowledgeDTO.builder()
                .id(1L).workspaceId(10L).title("部署指南").content("正文")
                .kbId(2L).directoryId(3L)
                .status(KnowledgeStatus.APPROVED.getValue())
                .version(7L)
                .build();
    }

    private ReviewEntity approvedReview(boolean withAi) {
        ReviewEntity r = new ReviewEntity();
        r.setId(1L);
        r.setWorkspaceId(10L);
        r.setKnowledgeId(1L);
        r.setVersion(7L);
        r.setStatus("APPROVED");
        if (withAi) {
            r.setAiTaskId(99L);
            r.setAiResultJson("[{\"severity\":\"info\",\"position\":\"L1\",\"evidence\":\"e\",\"suggestion\":\"s\"}]");
        }
        return r;
    }

    @Test
    void manualApprovedReview_canRelease() {
        when(contentApi.getEditorKnowledge(eq(10L), eq(1L))).thenReturn(approvedKnowledge());
        when(knowledgeApi.getKnowledgeBase(eq(10L), eq(2L)))
                .thenReturn(KnowledgeBaseVO.builder().id(2L).visibility(1).build());
        // 人工审核通过：无 AI 任务、无 AI 结果
        when(reviewMapper.selectOne(any())).thenReturn(approvedReview(false));
        when(contentApi.publishKnowledge(eq(10L), any())).thenReturn(true);

        var vo = service.release(CreateReleaseDTO.builder()
                .knowledgeId(1L).version(7L).build());

        assertThat(vo.getKnowledgeId()).isEqualTo(1L);
        assertThat(vo.getStatus()).isEqualTo("DONE");
        // 发布记录已建，且知识状态真正迁移
        ArgumentCaptor<ReleaseEntity> captor = ArgumentCaptor.forClass(ReleaseEntity.class);
        verify(releaseMapper).<ReleaseEntity>insert(captor.capture());
        verify(contentApi).publishKnowledge(eq(10L), any(KnowledgePublishDTO.class));
    }

    @Test
    void existingDoneRelease_returnsExisting_withoutRepublishing() {
        // 自动审核链路已发布（记录 DONE、知识 PUBLISHED）：审核中心重复点「发布」幂等返回，不再 409
        ReleaseEntity done = new ReleaseEntity();
        done.setId(11L);
        done.setWorkspaceId(10L);
        done.setKnowledgeId(1L);
        done.setVersion(7L);
        done.setStatus("DONE");
        when(contentApi.getEditorKnowledge(eq(10L), eq(1L))).thenReturn(
                EditorKnowledgeDTO.builder()
                        .id(1L).workspaceId(10L).title("部署指南").content("正文")
                        .kbId(2L).directoryId(3L)
                        .status(KnowledgeStatus.PUBLISHED.getValue())
                        .version(7L)
                        .build());
        when(releaseMapper.selectOne(any())).thenReturn(done);

        var vo = service.release(CreateReleaseDTO.builder()
                .knowledgeId(1L).version(7L).build());

        assertThat(vo.getId()).isEqualTo(11L);
        assertThat(vo.getStatus()).isEqualTo("DONE");
        verify(releaseMapper, never()).insert(any(ReleaseEntity.class));
        verify(contentApi, never()).publishKnowledge(any(), any());
    }

    @Test
    void noApprovedReview_throwsConflict() {
        when(contentApi.getEditorKnowledge(eq(10L), eq(1L))).thenReturn(approvedKnowledge());
        when(knowledgeApi.getKnowledgeBase(eq(10L), eq(2L)))
                .thenReturn(KnowledgeBaseVO.builder().id(2L).visibility(1).build());
        when(reviewMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.release(CreateReleaseDTO.builder()
                .knowledgeId(1L).version(7L).build()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void aiReviewWithErrorSeverity_stillBlocks() {
        when(contentApi.getEditorKnowledge(eq(10L), eq(1L))).thenReturn(approvedKnowledge());
        when(knowledgeApi.getKnowledgeBase(eq(10L), eq(2L)))
                .thenReturn(KnowledgeBaseVO.builder().id(2L).visibility(1).build());
        ReviewEntity withError = approvedReview(true);
        withError.setAiResultJson("[{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"e\",\"suggestion\":\"s\"}]");
        when(reviewMapper.selectOne(any())).thenReturn(withError);

        assertThatThrownBy(() -> service.release(CreateReleaseDTO.builder()
                .knowledgeId(1L).version(7L).build()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}