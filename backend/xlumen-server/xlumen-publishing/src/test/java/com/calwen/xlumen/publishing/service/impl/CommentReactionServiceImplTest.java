package com.calwen.xlumen.publishing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.publishing.dto.ReactionStateVO;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.entity.CommentReactionEntity;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import com.calwen.xlumen.publishing.mapper.CommentReactionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评论反应服务单元测试（F-0213）：三态互斥 toggle、评论存在性校验与批量聚合。
 *
 * @author calwen
 * @date 2026/8/18
 */
class CommentReactionServiceImplTest {

    /** eng_comment_reaction.reaction_type：1=点赞 2=点踩。 */
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_DISLIKE = 2;

    @Mock
    private CommentReactionMapper commentReactionMapper;

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentReactionServiceImpl commentReactionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 注册实体元数据：lambdaQuery().select() 的列解析需要 TableInfo（纯单元测试无 mapper 初始化）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CommentReactionEntity.class);
        WorkspaceContext.set(100L, 1L, "tester");
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void toggle_commentMissing_throwsNotFound() {
        when(commentMapper.selectById(300L)).thenReturn(null);

        assertThatThrownBy(() -> commentReactionService.toggleLike(300L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessageContaining("评论不存在");
        verifyNoInteractions(commentReactionMapper);
    }

    @Test
    void toggle_commentDeleted_throwsNotFound() {
        CommentEntity comment = new CommentEntity();
        comment.setId(300L);
        comment.setStatus(0);
        when(commentMapper.selectById(300L)).thenReturn(comment);

        assertThatThrownBy(() -> commentReactionService.toggleLike(300L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("评论不存在");
    }

    @Test
    void toggle_noRecord_insertsLike() {
        when(commentMapper.selectById(300L)).thenReturn(comment());
        when(commentReactionMapper.selectOne(any())).thenReturn(null);

        String reaction = commentReactionService.toggleLike(300L);

        assertThat(reaction).isEqualTo(ReactionStateVO.LIKE);
        ArgumentCaptor<CommentReactionEntity> captor = ArgumentCaptor.forClass(CommentReactionEntity.class);
        verify(commentReactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getCommentId()).isEqualTo(300L);
        assertThat(captor.getValue().getReactionType()).isEqualTo(TYPE_LIKE);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_likeWhileDisliking_switchesToLike() {
        when(commentMapper.selectById(300L)).thenReturn(comment());
        when(commentReactionMapper.selectOne(any())).thenReturn(row(TYPE_DISLIKE, 1));

        String reaction = commentReactionService.toggleLike(300L);

        assertThat(reaction).isEqualTo(ReactionStateVO.LIKE);
        ArgumentCaptor<CommentReactionEntity> captor = ArgumentCaptor.forClass(CommentReactionEntity.class);
        verify(commentReactionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReactionType()).isEqualTo(TYPE_LIKE);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_dislikeWhileDisliking_cancelsToNone() {
        when(commentMapper.selectById(300L)).thenReturn(comment());
        when(commentReactionMapper.selectOne(any())).thenReturn(row(TYPE_DISLIKE, 1));

        String reaction = commentReactionService.toggleDislike(300L);

        assertThat(reaction).isEqualTo(ReactionStateVO.NONE);
        ArgumentCaptor<CommentReactionEntity> captor = ArgumentCaptor.forClass(CommentReactionEntity.class);
        verify(commentReactionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void mapUserReactions_anonymous_returnsEmpty() {
        assertThat(commentReactionService.mapUserReactions(null, List.of(300L), null)).isEmpty();
    }

    @Test
    void mapUserReactions_mapsActiveReactionNames() {
        // 查询条件已过滤 status=1（活动反应），mock 只需返回活动行
        CommentReactionEntity like = row(TYPE_LIKE, 1);
        like.setCommentId(300L);
        when(commentReactionMapper.selectList(any())).thenReturn(List.of(like));

        Map<Long, String> result = commentReactionService.mapUserReactions(null, List.of(300L, 301L), 1L);

        assertThat(result).containsEntry(300L, ReactionStateVO.LIKE).hasSize(1);
    }

    @Test
    void countLikes_onlyCountsActiveLikeRows() {
        // 聚合防 N+1：mapper mock 返回两条点赞行（含跨空间），按 commentId 分组计数
        CommentReactionEntity a = row(TYPE_LIKE, 1);
        a.setCommentId(300L);
        CommentReactionEntity b = row(TYPE_LIKE, 1);
        b.setCommentId(300L);
        when(commentReactionMapper.selectList(any())).thenReturn(List.of(a, b));

        Map<Long, Long> counts = commentReactionService.countLikes(null, List.of(300L));

        assertThat(counts).containsEntry(300L, 2L);
    }

    private CommentEntity comment() {
        CommentEntity comment = new CommentEntity();
        comment.setId(300L);
        comment.setWorkspaceId(100L);
        comment.setKnowledgeId(200L);
        comment.setUserId(2L);
        comment.setStatus(1);
        return comment;
    }

    private CommentReactionEntity row(int reactionType, int status) {
        CommentReactionEntity reaction = new CommentReactionEntity();
        reaction.setId(1L);
        reaction.setWorkspaceId(100L);
        reaction.setCommentId(300L);
        reaction.setUserId(1L);
        reaction.setReactionType(reactionType);
        reaction.setStatus(status);
        return reaction;
    }
}
