package com.calwen.xlumen.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.calwen.xlumen.content.entity.KnowledgeEntity;
import com.calwen.xlumen.content.mapper.KnowledgeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公开读知识数统计单测：聚合结果映射 + 统计口径为已发布且未回收、跨空间（无 workspace 过滤）。
 *
 * @author calwen
 * @date 2026/9/14
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCountApiImplTest {

    @Mock
    private KnowledgeMapper knowledgeMapper;

    @InjectMocks
    private KnowledgeCountApiImpl knowledgeCountApi;

    @Test
    void countPublishedByKbIds_mapsCountsWithPublishedCrossSpaceFilter() {
        when(knowledgeMapper.selectMaps(any()))
                .thenReturn(List.of(Map.of("kbId", 5L, "cnt", 3L)));

        Map<Long, Long> counts = knowledgeCountApi.countPublishedByKbIds(List.of(5L));

        assertThat(counts).containsEntry(5L, 3L);
        String sql = capturedSql();
        assertThat(sql).contains("status").contains("recycle_status").contains("kb_id");
        // 公开口径跨空间：不得按 workspace 过滤
        assertThat(sql).doesNotContain("workspace_id");
    }

    @Test
    void countPublishedByDirectoryIds_mapsCountsWithPublishedFilter() {
        when(knowledgeMapper.selectMaps(any()))
                .thenReturn(List.of(Map.of("directoryId", 11L, "cnt", 2L)));

        Map<Long, Long> counts = knowledgeCountApi.countPublishedByDirectoryIds(5L, List.of(11L));

        assertThat(counts).containsEntry(11L, 2L);
        String sql = capturedSql();
        assertThat(sql).contains("status").contains("recycle_status").contains("directory_id");
        assertThat(sql).doesNotContain("workspace_id");
    }

    @SuppressWarnings("unchecked")
    private String capturedSql() {
        ArgumentCaptor<Wrapper<KnowledgeEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(knowledgeMapper).selectMaps(captor.capture());
        return captor.getValue().getSqlSegment();
    }
}
