package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语义检索段落聚合单测：按知识聚合、片段拼接、最高分、首个锚点、计数。
 *
 * @author calwen
 * @date 2026/8/26
 */
class PublicKnowledgeServiceImplTest {

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
}