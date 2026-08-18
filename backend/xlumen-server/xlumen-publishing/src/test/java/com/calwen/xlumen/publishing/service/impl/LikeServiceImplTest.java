package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.publishing.dto.ReactionStateVO;
import com.calwen.xlumen.publishing.entity.LikeEntity;
import com.calwen.xlumen.publishing.mapper.LikeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识反应服务单元测试（F-0212）：三态互斥 toggle 语义
 * （无反应->激活、同类型->取消、异类型->切换）。
 *
 * @author calwen
 * @date 2026/8/18
 */
class LikeServiceImplTest {

    /** eng_like.reaction_type：1=点赞 2=点踩。 */
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_DISLIKE = 2;

    @Mock
    private LikeMapper likeMapper;

    @InjectMocks
    private LikeServiceImpl likeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        WorkspaceContext.set(100L, 1L, "tester");
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void toggle_noRecord_insertsLike() {
        when(likeMapper.selectOne(any())).thenReturn(null);

        String reaction = likeService.toggleLike(200L);

        assertThat(reaction).isEqualTo(ReactionStateVO.LIKE);
        ArgumentCaptor<LikeEntity> captor = ArgumentCaptor.forClass(LikeEntity.class);
        verify(likeMapper).insert(captor.capture());
        assertThat(captor.getValue().getKnowledgeId()).isEqualTo(200L);
        assertThat(captor.getValue().getReactionType()).isEqualTo(TYPE_LIKE);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_anonymous_throwsUnauthorized() {
        WorkspaceContext.clear();

        assertThatThrownBy(() -> likeService.toggleLike(200L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先登录");
    }

    @Test
    void toggle_likeAfterCancelled_reactivatesLike() {
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_LIKE, 0));

        String reaction = likeService.toggleLike(200L);

        assertThat(reaction).isEqualTo(ReactionStateVO.LIKE);
        ArgumentCaptor<LikeEntity> captor = ArgumentCaptor.forClass(LikeEntity.class);
        verify(likeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReactionType()).isEqualTo(TYPE_LIKE);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_likeWhileLiking_cancelsToNone() {
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_LIKE, 1));

        String reaction = likeService.toggleLike(200L);

        assertThat(reaction).isEqualTo(ReactionStateVO.NONE);
        ArgumentCaptor<LikeEntity> captor = ArgumentCaptor.forClass(LikeEntity.class);
        verify(likeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void toggle_dislikeWhileLiking_switchesToDislike() {
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_LIKE, 1));

        String reaction = likeService.toggleDislike(200L);

        assertThat(reaction).isEqualTo(ReactionStateVO.DISLIKE);
        ArgumentCaptor<LikeEntity> captor = ArgumentCaptor.forClass(LikeEntity.class);
        verify(likeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReactionType()).isEqualTo(TYPE_DISLIKE);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_dislikeWhileDisliking_cancelsToNone() {
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_DISLIKE, 1));

        String reaction = likeService.toggleDislike(200L);

        assertThat(reaction).isEqualTo(ReactionStateVO.NONE);
        ArgumentCaptor<LikeEntity> captor = ArgumentCaptor.forClass(LikeEntity.class);
        verify(likeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void toggle_dislikeAfterCancelled_reactivatesDislike_thenLikeCancels() {
        // 取消后再点赞（赞->踩->取消踩->赞 的收尾路径）：无活动反应时激活点赞
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_DISLIKE, 0));

        String reaction = likeService.toggleLike(200L);

        assertThat(reaction).isEqualTo(ReactionStateVO.LIKE);
        ArgumentCaptor<LikeEntity> captor = ArgumentCaptor.forClass(LikeEntity.class);
        verify(likeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReactionType()).isEqualTo(TYPE_LIKE);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void currentReaction_returnsActiveTypeOrNone() {
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_DISLIKE, 1));
        assertThat(likeService.currentReaction(200L)).isEqualTo(ReactionStateVO.DISLIKE);

        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_LIKE, 0));
        assertThat(likeService.currentReaction(200L)).isEqualTo(ReactionStateVO.NONE);

        when(likeMapper.selectOne(any())).thenReturn(null);
        assertThat(likeService.currentReaction(200L)).isEqualTo(ReactionStateVO.NONE);
    }

    @Test
    void isLiked_onlyWhenLikeTypeActive() {
        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_DISLIKE, 1));
        assertThat(likeService.isLiked(100L, 200L, 1L)).isFalse();

        when(likeMapper.selectOne(any())).thenReturn(row(TYPE_LIKE, 1));
        assertThat(likeService.isLiked(100L, 200L, 1L)).isTrue();

        assertThat(likeService.isLiked(100L, 200L, null)).isFalse();
    }

    /** 构造已有反应行（复用同一实体模拟 toggle 前状态）。 */
    private LikeEntity row(int reactionType, int status) {
        LikeEntity like = new LikeEntity();
        like.setId(1L);
        like.setWorkspaceId(100L);
        like.setKnowledgeId(200L);
        like.setUserId(1L);
        like.setReactionType(reactionType);
        like.setStatus(status);
        return like;
    }
}
