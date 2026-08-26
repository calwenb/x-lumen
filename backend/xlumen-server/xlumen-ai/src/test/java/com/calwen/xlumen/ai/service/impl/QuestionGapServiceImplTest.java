package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.entity.QuestionGapEntity;
import com.calwen.xlumen.ai.mapper.QuestionGapMapper;
import com.calwen.xlumen.ai.service.QuestionGapService;
import com.calwen.xlumen.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 问答知识缺口服务单测：正常落库、访客跳过、追问多行落库、归属校验置为已处理。
 *
 * @author calwen
 * @date 2026/8/26
 */
class QuestionGapServiceImplTest {

    private QuestionGapMapper mapper;
    private QuestionGapServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(QuestionGapMapper.class);
        service = new QuestionGapServiceImpl(mapper);
    }

    @Test
    void recordUnmatched_normal_insertsWithSourceAndStatus() {
        service.recordUnmatched(1L, 2L, 3L, "  什么是向量检索？  ");

        ArgumentCaptor<QuestionGapEntity> captor = ArgumentCaptor.forClass(QuestionGapEntity.class);
        verify(mapper).insert(captor.capture());
        QuestionGapEntity entity = captor.getValue();
        assertThat(entity.getWorkspaceId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(2L);
        assertThat(entity.getConversationId()).isEqualTo(3L);
        assertThat(entity.getQuestion()).isEqualTo("什么是向量检索？");
        assertThat(entity.getSource()).isEqualTo(QuestionGapService.SOURCE_ANSWER_UNMATCHED);
        assertThat(entity.getStatus()).isEqualTo(QuestionGapService.STATUS_UNHANDLED);
    }

    @Test
    void recordUnmatched_nullUser_skips() {
        service.recordUnmatched(1L, null, 3L, "问题");
        verifyNoInteractions(mapper);
    }

    @Test
    void recordFollowups_insertsEachRow() {
        service.recordFollowups(1L, 2L, 3L, List.of("追问一", "追问二", "追问三"));

        verify(mapper, times(3)).insert(any(QuestionGapEntity.class));
        ArgumentCaptor<QuestionGapEntity> captor = ArgumentCaptor.forClass(QuestionGapEntity.class);
        verify(mapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(e -> {
            assertThat(e.getUserId()).isEqualTo(2L);
            assertThat(e.getSource()).isEqualTo(QuestionGapService.SOURCE_FOLLOWUP_SUGGESTED);
            assertThat(e.getStatus()).isEqualTo(QuestionGapService.STATUS_UNHANDLED);
        });
    }

    @Test
    void recordFollowups_emptyList_skips() {
        service.recordFollowups(1L, 2L, 3L, List.of());
        verifyNoInteractions(mapper);
    }

    @Test
    void markHandled_workspaceMismatch_throwsNotFound() {
        QuestionGapEntity entity = new QuestionGapEntity();
        entity.setId(9L);
        entity.setWorkspaceId(1L);
        when(mapper.selectById(9L)).thenReturn(entity);

        assertThatThrownBy(() -> service.markHandled(2L, 9L))
                .isInstanceOf(BizException.class);
        verify(mapper, never()).updateById(any(QuestionGapEntity.class));
    }

    @Test
    void markHandled_owned_setsHandled() {
        QuestionGapEntity entity = new QuestionGapEntity();
        entity.setId(9L);
        entity.setWorkspaceId(1L);
        entity.setStatus(QuestionGapService.STATUS_UNHANDLED);
        when(mapper.selectById(9L)).thenReturn(entity);

        service.markHandled(1L, 9L);
        assertThat(entity.getStatus()).isEqualTo(QuestionGapService.STATUS_HANDLED);
        verify(mapper).updateById(entity);
    }
}