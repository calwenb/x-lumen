package com.calwen.xlumen.publishing.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.publishing.dto.CommentQueryDTO;
import com.calwen.xlumen.publishing.dto.CommentVO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import com.calwen.xlumen.publishing.service.CommentReactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 评论列表聚合单元测试（F-0213）：listComments 批量填充赞/踩计数与当前用户反应（防 N+1）。
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
