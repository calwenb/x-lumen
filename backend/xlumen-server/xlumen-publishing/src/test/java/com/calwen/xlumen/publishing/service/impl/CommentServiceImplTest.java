package com.calwen.xlumen.publishing.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.CommentQueryDTO;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.CreateCommentDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.event.CommentAiEchoRequestedEvent;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import com.calwen.xlumen.publishing.service.CommentReactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论服务单元测试：listComments 批量填充赞/踩计数与当前用户反应（防 N+1）；
 * createComment 命中 @小光 时发布 AI 回声事件、未命中不发布（评论主流程不回滚）。
 *
 * @author calwen
 * @date 2026/8/18
 */
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private WorkspaceApi workspaceApi;

    @Mock
    private CommentReactionService commentReactionService;

    @Mock
    private ContentApi contentApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void listComments_loggedIn_aggregatesCountsAndMyReaction() {
        WorkspaceContext.set(100L, 1L, "tester");
        CommentEntity first = comment(300L);
        CommentEntity second = comment(301L);
        when(commentMapper.selectPage(any(), any())).thenReturn(pageOf(first, second));
        when(commentReactionService.countLikes(isNull(), anyList()))
                .thenReturn(Map.of(300L, 3L));
        when(commentReactionService.countDislikes(isNull(), anyList()))
                .thenReturn(Map.of(301L, 2L));
        when(commentReactionService.mapUserReactions(isNull(), anyList(), eq(1L)))
                .thenReturn(Map.of(300L, "LIKE"));

        PageResult<CommentVO> result = commentService.listComments(200L, query());

        assertThat(result.getRecords()).hasSize(2);
        CommentVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(300L);
        assertThat(vo.getLikeCount()).isEqualTo(3L);
        assertThat(vo.getDislikeCount()).isZero();
        assertThat(vo.getMyReaction()).isEqualTo("LIKE");
        CommentVO vo2 = result.getRecords().get(1);
        assertThat(vo2.getLikeCount()).isZero();
        assertThat(vo2.getDislikeCount()).isEqualTo(2L);
        assertThat(vo2.getMyReaction()).isNull();
    }

    @Test
    void listComments_anonymous_myReactionIsNull() {
        CommentEntity comment = comment(300L);
        when(commentMapper.selectPage(any(), any())).thenReturn(pageOf(comment));
        when(commentReactionService.countLikes(isNull(), anyList())).thenReturn(Map.of(300L, 1L));
        when(commentReactionService.countDislikes(isNull(), anyList())).thenReturn(Map.of());
        when(commentReactionService.mapUserReactions(isNull(), anyList(), isNull())).thenReturn(Map.of());

        PageResult<CommentVO> result = commentService.listComments(200L, query());

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getMyReaction()).isNull();
        assertThat(result.getRecords().get(0).getLikeCount()).isEqualTo(1L);
    }

    @Test
    void createComment_withAiMention_publishesAiEchoEvent() {
        WorkspaceContext.set(100L, 1L, "tester");
        EditorKnowledgeDTO knowledge = EditorKnowledgeDTO.builder()
                .id(200L).workspaceId(100L).title("部署指南").content("正文快照").build();
        when(contentApi.getEditorKnowledge(100L, 200L)).thenReturn(knowledge);

        CommentVO vo = commentService.createComment(200L,
                CreateCommentDTO.builder().content("这篇文章写得很好 @小光").build());

        assertThat(vo.getId()).isNotNull();
        assertThat(vo.getIsAi()).isFalse();
        ArgumentCaptor<CommentAiEchoRequestedEvent> captor =
                ArgumentCaptor.forClass(CommentAiEchoRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CommentAiEchoRequestedEvent event = captor.getValue();
        assertThat(event.getWorkspaceId()).isEqualTo(100L);
        assertThat(event.getKnowledgeId()).isEqualTo(200L);
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getCommentId()).isEqualTo(vo.getId());
        assertThat(event.getCommentContent()).isEqualTo("这篇文章写得很好 @小光");
        assertThat(event.getUsername()).isEqualTo("tester");
        assertThat(event.getKnowledgeTitle()).isEqualTo("部署指南");
    }

    @Test
    void createComment_withFullWidthAiMention_publishesAiEchoEvent() {
        WorkspaceContext.set(100L, 1L, "tester");
        when(contentApi.getEditorKnowledge(100L, 200L)).thenReturn(null);

        commentService.createComment(200L, CreateCommentDTO.builder().content("＠小光 请问怎么配置").build());

        ArgumentCaptor<CommentAiEchoRequestedEvent> captor =
                ArgumentCaptor.forClass(CommentAiEchoRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getCommentContent()).isEqualTo("＠小光 请问怎么配置");
        assertThat(captor.getValue().getKnowledgeTitle()).isNull();
    }

    @Test
    void createComment_withoutAiMention_doesNotPublishAiEchoEvent() {
        WorkspaceContext.set(100L, 1L, "tester");

        commentService.createComment(200L,
                CreateCommentDTO.builder().content("写得不错，学习了").build());

        verify(eventPublisher, never()).publishEvent(any());
        verify(contentApi, never()).getEditorKnowledge(any(), any());
    }

    private CommentQueryDTO query() {
        return CommentQueryDTO.builder().pageNo(1).pageSize(10).build();
    }

    private CommentEntity comment(Long id) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setWorkspaceId(100L);
        comment.setKnowledgeId(200L);
        comment.setUserId(2L);
        comment.setUserName("reader");
        comment.setContent("写得不错");
        comment.setStatus(1);
        comment.setCreatedAt(LocalDateTime.of(2026, 8, 18, 12, 0));
        return comment;
    }

    private Page<CommentEntity> pageOf(CommentEntity... records) {
        Page<CommentEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(records));
        page.setTotal(records.length);
        return page;
    }
}
