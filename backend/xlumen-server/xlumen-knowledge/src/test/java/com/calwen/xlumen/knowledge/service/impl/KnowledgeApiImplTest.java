package com.calwen.xlumen.knowledge.service.impl;

import com.calwen.xlumen.knowledge.api.KnowledgeCountApi;
import com.calwen.xlumen.knowledge.entity.KbDirectoryEntity;
import com.calwen.xlumen.knowledge.mapper.KbDirectoryMapper;
import com.calwen.xlumen.knowledge.service.DirectoryService;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import com.calwen.xlumen.knowledge.service.KnowledgeBaseService;
import com.calwen.xlumen.knowledge.service.RecycleBinService;
import com.calwen.xlumen.knowledge.service.RetrievalService;
import com.calwen.xlumen.knowledge.service.VisibilityService;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 公开读统计单测：公开目录树按已发布口径构造新 VO（不复用认证路径语义）、
 * 公开库已发布知识数取反向 SPI 聚合，缺失提供方时回退 0/空。
 *
 * @author calwen
 * @date 2026/9/14
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeApiImplTest {

    @Mock
    private IndexPipelineService indexPipelineService;
    @Mock
    private RetrievalService retrievalService;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private DirectoryService directoryService;
    @Mock
    private VisibilityService visibilityService;
    @Mock
    private RecycleBinService recycleBinService;
    @Mock
    private KbDirectoryMapper directoryMapper;
    @Mock
    private ObjectProvider<KnowledgeCountApi> knowledgeCountApiProvider;

    @InjectMocks
    private KnowledgeApiImpl knowledgeApi;

    @Test
    void getPublishedDirectoryTree_buildsNestedTreeWithPublishedCounts() {
        when(directoryMapper.selectList(any())).thenReturn(List.of(
                directory(1L, 5L, 0L, "A"),
                directory(2L, 5L, 1L, "B")));
        KnowledgeCountApi counter = org.mockito.Mockito.mock(KnowledgeCountApi.class);
        when(knowledgeCountApiProvider.getIfAvailable()).thenReturn(counter);
        when(counter.countPublishedByDirectoryIds(5L, List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 3L, 2L, 1L));

        List<DirectoryVO> tree = knowledgeApi.getPublishedDirectoryTree(5L);

        assertThat(tree).hasSize(1);
        DirectoryVO root = tree.get(0);
        assertThat(root.getId()).isEqualTo(1L);
        assertThat(root.getKnowledgeCount()).isEqualTo(3L);
        assertThat(root.getChildren()).hasSize(1);
        assertThat(root.getChildren().get(0).getId()).isEqualTo(2L);
        assertThat(root.getChildren().get(0).getKnowledgeCount()).isEqualTo(1L);
    }

    @Test
    void getPublishedDirectoryTree_emptyWhenNoDirectories() {
        when(directoryMapper.selectList(any())).thenReturn(List.of());

        assertThat(knowledgeApi.getPublishedDirectoryTree(5L)).isEmpty();
    }

    @Test
    void countPublishedKnowledge_delegatesToReverseSpi() {
        KnowledgeCountApi counter = org.mockito.Mockito.mock(KnowledgeCountApi.class);
        when(knowledgeCountApiProvider.getIfAvailable()).thenReturn(counter);
        when(counter.countPublishedByKbIds(List.of(5L))).thenReturn(Map.of(5L, 7L));

        assertThat(knowledgeApi.countPublishedKnowledge(5L)).isEqualTo(7L);
    }

    private KbDirectoryEntity directory(Long id, Long kbId, Long parentId, String name) {
        KbDirectoryEntity entity = new KbDirectoryEntity();
        entity.setId(id);
        entity.setKbId(kbId);
        entity.setParentId(parentId);
        entity.setName(name);
        return entity;
    }
}
