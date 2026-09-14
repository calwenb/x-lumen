package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 公开读服务单测：语义检索段落聚合（按知识聚合、片段拼接、最高分、首个锚点、计数）
 * 与公开库详情（已发布计数 + 公开目录树 + 私有库统一 404）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@ExtendWith(MockitoExtension.class)
class PublicKnowledgeServiceImplTest {

    @Mock
    private KnowledgeApi knowledgeApi;

    @InjectMocks
    private PublicKnowledgeServiceImpl publicKnowledgeService;

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