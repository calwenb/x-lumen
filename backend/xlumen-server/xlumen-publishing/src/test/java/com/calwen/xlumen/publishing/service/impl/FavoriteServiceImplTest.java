package com.calwen.xlumen.publishing.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.entity.FavoriteEntity;
import com.calwen.xlumen.publishing.mapper.FavoriteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识收藏服务单元测试（F-0212）：toggle 幂等 + 收藏列表可见性过滤。
 *
 * @author calwen
 * @date 2026/8/18
 */
class FavoriteServiceImplTest {

    @Mock
    private FavoriteMapper favoriteMapper;

    @Mock
    private ContentApi contentApi;

    @Mock
    private KnowledgeApi knowledgeApi;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

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
    void toggle_noRecord_insertsFavorite() {
        when(favoriteMapper.selectOne(any())).thenReturn(null);

        boolean favored = favoriteService.toggleFavorite(200L);

        assertThat(favored).isTrue();
        ArgumentCaptor<FavoriteEntity> captor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(favoriteMapper).insert(captor.capture());
        assertThat(captor.getValue().getKnowledgeId()).isEqualTo(200L);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_favorited_cancels() {
        when(favoriteMapper.selectOne(any())).thenReturn(row(200L, 1));

        assertThat(favoriteService.toggleFavorite(200L)).isFalse();

        ArgumentCaptor<FavoriteEntity> captor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(favoriteMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void toggle_cancelled_refavorites() {
        when(favoriteMapper.selectOne(any())).thenReturn(row(200L, 0));

        assertThat(favoriteService.toggleFavorite(200L)).isTrue();

        ArgumentCaptor<FavoriteEntity> captor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(favoriteMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void toggle_anonymous_throwsUnauthorized() {
        WorkspaceContext.clear();

        assertThatThrownBy(() -> favoriteService.toggleFavorite(200L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先登录");
    }

    @Test
    void listFavorites_filtersInvisibleKnowledgeAndCarriesFavoritedAt() {
        when(knowledgeApi.resolveVisibleKbIds(1L)).thenReturn(List.of(10L));
        when(favoriteMapper.selectPage(any(), any())).thenReturn(pageOf(
                row(200L, 1), row(201L, 1)));
        // 200 可见（已发布且库可见），201 经可见库集合过滤后不可见（getPublished 返回 null）
        when(contentApi.getPublished(isNull(), eq(200L), any())).thenReturn(published(200L));
        when(contentApi.getPublished(isNull(), eq(201L), any())).thenReturn(null);
        when(knowledgeApi.getKnowledgeBaseById(10L)).thenReturn(kb("测试库"));

        PageResult<KnowledgeCardVO> result = favoriteService.listFavorites(query());

        assertThat(result.getRecords()).hasSize(1);
        KnowledgeCardVO card = result.getRecords().get(0);
        assertThat(card.getId()).isEqualTo(200L);
        assertThat(card.getKbName()).isEqualTo("测试库");
        assertThat(card.getFavoritedAt()).isNotNull();
        // total 仍按收藏行数返回（内存过滤 MVP 取舍）
        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void listFavorites_anonymous_throwsUnauthorized() {
        WorkspaceContext.clear();

        assertThatThrownBy(() -> favoriteService.listFavorites(query()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先登录");
    }

    @Test
    void isFavorited_onlyActiveRowCounts() {
        when(favoriteMapper.selectOne(any())).thenReturn(row(200L, 1));
        assertThat(favoriteService.isFavorited(100L, 200L, 1L)).isTrue();

        when(favoriteMapper.selectOne(any())).thenReturn(row(200L, 0));
        assertThat(favoriteService.isFavorited(100L, 200L, 1L)).isFalse();

        assertThat(favoriteService.isFavorited(100L, 200L, null)).isFalse();
    }

    private PageQueryDTO query() {
        PageQueryDTO query = new PageQueryDTO();
        query.setPageNo(1);
        query.setPageSize(10);
        return query;
    }

    /** 构造收藏行（status=1 已收藏）。 */
    private FavoriteEntity row(Long knowledgeId, int status) {
        FavoriteEntity favorite = new FavoriteEntity();
        favorite.setId(knowledgeId);
        favorite.setWorkspaceId(100L);
        favorite.setKnowledgeId(knowledgeId);
        favorite.setUserId(1L);
        favorite.setStatus(status);
        favorite.setCreatedAt(LocalDateTime.of(2026, 8, 18, 12, 0));
        return favorite;
    }

    private Page<FavoriteEntity> pageOf(FavoriteEntity... records) {
        Page<FavoriteEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(records));
        page.setTotal(records.length);
        return page;
    }

    private KnowledgeDetailDTO published(Long knowledgeId) {
        return KnowledgeDetailDTO.builder()
                .id(knowledgeId).title("知识" + knowledgeId).summary("摘要")
                .authorName("author").kbId(10L).directoryId(0L)
                .tags(List.of()).viewCount(0L).readMinutes(5)
                .publishedAt(LocalDateTime.of(2026, 8, 1, 9, 0)).build();
    }

    private KnowledgeBaseVO kb(String name) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(10L);
        vo.setWorkspaceId(100L);
        vo.setName(name);
        return vo;
    }
}
