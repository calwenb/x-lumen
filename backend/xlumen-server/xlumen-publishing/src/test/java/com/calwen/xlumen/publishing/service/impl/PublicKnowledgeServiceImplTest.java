package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 公开读服务单测：语义检索相关度裁剪与段落聚合（按知识聚合、片段拼接、最高分、首个锚点、计数）、
 * 语义无相关知识不回退关键词，以及公开库详情（已发布计数 + 公开目录树 + 私有库统一 404）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@ExtendWith(MockitoExtension.class)
class PublicKnowledgeServiceImplTest {

    /** 相关度下限（xlumen.retrieval-min-score 的测试值）。 */
    private static final float MIN_SCORE = 0.40f;

    @Mock
    private KnowledgeApi knowledgeApi;

    @Mock
    private ContentApi contentApi;

    @InjectMocks
    private PublicKnowledgeServiceImpl publicKnowledgeService;

    /** 语义检索入参（可见库集合由 mock 返回）。 */
    private KnowledgeQueryDTO semanticQuery() {
        return KnowledgeQueryDTO.builder()
                .keyword("防重复提交").mode("semantic").pageNo(1).pageSize(50).build();
    }

    @Test
    void semanticSearch_dropsHitsBelowRelevanceFloorAndKeepsKnowledgeOrder() {
        ReflectionTestUtils.setField(publicKnowledgeService, "retrievalMinScore", MIN_SCORE);
        when(knowledgeApi.resolveVisibleKbIds(null)).thenReturn(List.of(7L));
        when(knowledgeApi.search(any())).thenReturn(List.of(
                SearchResultDTO.builder().knowledgeId(1L).title("Redis 防重").headingAnchor("结论")
                        .chunkText("SETNX 只是第一步").score(0.52f).build(),
                SearchResultDTO.builder().knowledgeId(2L).title("无关知识").headingAnchor("")
                        .chunkText("相似度偏低的段落").score(0.31f).build(),
                SearchResultDTO.builder().knowledgeId(1L).title("Redis 防重").headingAnchor("Lua")
                        .chunkText("校验与删除原子化").score(0.44f).build()));

        PageResult<KnowledgeCardVO> page = publicKnowledgeService.listKnowledge(semanticQuery());

        // 0.31 的无关知识被裁掉；同一知识的两段（0.52/0.44）都在下限之上，计数与最高分照常聚合
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getId()).isEqualTo(1L);
        assertThat(page.getRecords().get(0).getSemanticScore()).isEqualTo(0.52f);
        assertThat(page.getRecords().get(0).getChunkCount()).isEqualTo(2);
        assertThat(page.getTotal()).isEqualTo(1L);
    }

    @Test
    void semanticSearch_allHitsBelowFloor_returnsEmptyPageWithoutKeywordFallback() {
        ReflectionTestUtils.setField(publicKnowledgeService, "retrievalMinScore", MIN_SCORE);
        when(knowledgeApi.resolveVisibleKbIds(null)).thenReturn(List.of(7L));
        when(knowledgeApi.search(any())).thenReturn(List.of(
                SearchResultDTO.builder().knowledgeId(1L).title("无关知识").chunkText("整库最近邻").score(0.28f).build()));

        PageResult<KnowledgeCardVO> page = publicKnowledgeService.listKnowledge(semanticQuery());

        // 链路正常但无相关知识：空页（而非回退关键词——语义页不该展示关键词结果）
        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
        verifyNoInteractions(contentApi);
    }

    @Test
    void aggregateSemantic_groupsByKnowledgeWithScoreAndAnchor() {
        List<SearchResultDTO> results = List.of(
                SearchResultDTO.builder().knowledgeId(1L).title("部署指南").chunkSeq(0)
                        .headingAnchor("安装").chunkText("第一步安装").score(0.9f).build(),
                SearchResultDTO.builder().knowledgeId(1L).title("部署指南").chunkSeq(1)
                        .headingAnchor("配置").chunkText("第二步配置").score(0.7f).build(),
                SearchResultDTO.builder().knowledgeId(2L).title("架构说明").chunkSeq(0)
                        .headingAnchor("").chunkText("整体架构").score(0.5f).build(),
                SearchResultDTO.builder().knowledgeId(null).chunkSeq(0).chunkText("无归属").score(0.4f).build());

        List<KnowledgeCardVO> cards = PublicKnowledgeServiceImpl.aggregateSemantic(results);

        assertThat(cards).hasSize(2);
        KnowledgeCardVO first = cards.get(0);
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(first.getSummary()).contains("安装", "配置");
        assertThat(first.getChunkCount()).isEqualTo(2);
        assertThat(first.getSemanticScore()).isEqualTo(0.9f);
        assertThat(first.getFirstAnchor()).isEqualTo("安装");
        assertThat(cards.get(1).getChunkCount()).isEqualTo(1);
        assertThat(cards.get(1).getSemanticScore()).isEqualTo(0.5f);
    }

    @Test
    void getKnowledgeBase_publicKb_fillsPublishedCountAndDirectories() {
        when(knowledgeApi.getKnowledgeBaseById(7L)).thenReturn(KnowledgeBaseVO.builder()
                .id(7L).workspaceId(3L).name("公开库").visibility(1).knowledgeCount(0L).build());
        when(knowledgeApi.countPublishedKnowledge(7L)).thenReturn(5L);
        when(knowledgeApi.getPublishedDirectoryTree(7L)).thenReturn(List.of(DirectoryVO.builder()
                .id(11L).kbId(7L).parentId(0L).name("目录一").knowledgeCount(3L).children(List.of()).build()));

        KnowledgeBaseVO kb = publicKnowledgeService.getKnowledgeBase(7L);

        assertThat(kb.getKnowledgeCount()).isEqualTo(5L);
        assertThat(kb.getDirectories()).hasSize(1);
        assertThat(kb.getDirectories().get(0).getKnowledgeCount()).isEqualTo(3L);
    }

    @Test
    void getKnowledgeBase_privateKb_notFoundWithoutLoosening() {
        when(knowledgeApi.getKnowledgeBaseById(7L)).thenReturn(KnowledgeBaseVO.builder()
                .id(7L).visibility(0).build());

        assertThatThrownBy(() -> publicKnowledgeService.getKnowledgeBase(7L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("知识库不存在或无权访问");
        // 私有库不得触发公开计数/目录查询，避免泄露存在性
        org.mockito.Mockito.verify(knowledgeApi, org.mockito.Mockito.never()).countPublishedKnowledge(7L);
    }
}