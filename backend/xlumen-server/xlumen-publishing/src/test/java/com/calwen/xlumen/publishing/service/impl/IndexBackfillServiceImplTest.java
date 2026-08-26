package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.publishing.dto.ReindexAllVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 索引补跑服务单测：reindexAll 逐条重建、单条失败不中断、跨空间/非发布跳过。
 *
 * @author calwen
 * @date 2026/8/26
 */
class IndexBackfillServiceImplTest {

    private ContentApi contentApi;
    private KnowledgeApi knowledgeApi;
    private IndexBackfillServiceImpl service;

    @BeforeEach
    void setUp() {
        contentApi = mock(ContentApi.class);
        knowledgeApi = mock(KnowledgeApi.class);
        service = new IndexBackfillServiceImpl();
        ReflectionTestUtils.setField(service, "contentApi", contentApi);
        ReflectionTestUtils.setField(service, "knowledgeApi", knowledgeApi);
        WorkspaceContext.set(1L, 100L, "owner");
    }

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    private static EditorKnowledgeDTO editor(Long id, int status) {
        return EditorKnowledgeDTO.builder().id(id).status(status).version(3L).kbId(10L)
                .title("T" + id).content("正文" + id).build();
    }

    @Test
    void reindexAll_skipsForeignAndUnpublished_reportsFailures() {
        when(knowledgeApi.resolveVisibleKbIds(anyLong())).thenReturn(List.of(10L, 20L));
        when(contentApi.listPublished(any(), any())).thenReturn(ContentPageResult.<PublishedKnowledgeDTO>builder()
                .records(List.of(
                        PublishedKnowledgeDTO.builder().id(100L).build(),
                        PublishedKnowledgeDTO.builder().id(101L).build(),
                        PublishedKnowledgeDTO.builder().id(200L).build()))
                .total(3).build());
        // 100 属本空间已发布；101 属本空间但重建抛错；200 属其他空间（getEditorKnowledge 返回 null → 跳过）
        when(contentApi.getEditorKnowledge(1L, 100L)).thenReturn(editor(100L, KnowledgeStatus.PUBLISHED.getValue()));
        when(contentApi.getEditorKnowledge(1L, 101L)).thenReturn(editor(101L, KnowledgeStatus.PUBLISHED.getValue()));
        when(contentApi.getEditorKnowledge(1L, 200L)).thenReturn(null);
        doAnswer(inv -> {
            IndexRequestDTO req = inv.getArgument(0);
            if (req.getKnowledgeId().equals(101L)) {
                throw new IllegalStateException("embedding 失败");
            }
            return null;
        }).when(knowledgeApi).reindexKnowledge(any(IndexRequestDTO.class));

        ReindexAllVO vo = service.reindexAll();

        assertThat(vo.getTotal()).isEqualTo(2);
        assertThat(vo.getOk()).isEqualTo(1);
        assertThat(vo.getFailed()).hasSize(1);
        assertThat(vo.getFailed().get(0).getKnowledgeId()).isEqualTo(101L);
        assertThat(vo.getFailed().get(0).getReason()).contains("embedding");
    }
}