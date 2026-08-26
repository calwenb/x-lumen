package com.calwen.xlumen.knowledge.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milvus 过滤表达式单测：workspaceId 为空时省略空间子句（跨空间可见库聚合检索用）。
 *
 * @author calwen
 * @date 2026/8/26
 */
class MilvusVectorStoreTest {

    @Test
    void buildFilter_withWorkspace_includesWsClause() {
        assertThat(MilvusVectorStore.buildFilter(1L, List.of(10L, 11L), null))
                .isEqualTo("workspace_id == \"1\" && kb_id in [\"10\", \"11\"]");
    }

    @Test
    void buildFilter_nullWorkspace_omitsWsClause() {
        assertThat(MilvusVectorStore.buildFilter(null, List.of(10L, 11L), null))
                .isEqualTo("kb_id in [\"10\", \"11\"]");
    }

    @Test
    void buildFilter_withKnowledgeId_appendsArticleClause() {
        assertThat(MilvusVectorStore.buildFilter(null, List.of(10L), 99L))
                .isEqualTo("kb_id in [\"10\"] && article_id == \"99\"");
    }
}