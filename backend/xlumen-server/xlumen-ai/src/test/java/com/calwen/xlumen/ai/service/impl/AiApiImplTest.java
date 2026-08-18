package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.entity.AiEnhanceResultEntity;
import com.calwen.xlumen.ai.mapper.AiEnhanceResultMapper;
import com.calwen.xlumen.ai.service.AiTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AI 模块对外接口单元测试（F-0808）：findLatestSummary 取最新一条并解析 summary 字段。
 *
 * @author calwen
 * @date 2026/8/18
 */
class AiApiImplTest {

    @Mock
    private AiTaskService aiTaskService;

    @Mock
    private AiEnhanceResultMapper enhanceResultMapper;

    private AiApiImpl aiApi;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiApi = new AiApiImpl(aiTaskService, enhanceResultMapper);
    }

    @Test
    void findLatestSummary_parsesSummaryFromLatestRow() {
        // selectOne 带 created_at 倒序 LIMIT 1，mapper 返回即最新一条
        when(enhanceResultMapper.selectOne(any())).thenReturn(result("{\"summary\":\"最新摘要\"}"));

        String summary = aiApi.findLatestSummary(100L, 200L);

        assertThat(summary).isEqualTo("最新摘要");
    }

    @Test
    void findLatestSummary_noRow_returnsNull() {
        when(enhanceResultMapper.selectOne(any())).thenReturn(null);

        assertThat(aiApi.findLatestSummary(100L, 200L)).isNull();
    }

    @Test
    void findLatestSummary_blankSummary_returnsNull() {
        when(enhanceResultMapper.selectOne(any())).thenReturn(result("{\"summary\":\"\"}"));

        assertThat(aiApi.findLatestSummary(100L, 200L)).isNull();
    }

    @Test
    void findLatestSummary_illegalJson_returnsNull() {
        when(enhanceResultMapper.selectOne(any())).thenReturn(result("脏数据"));

        assertThat(aiApi.findLatestSummary(100L, 200L)).isNull();
    }

    @Test
    void findLatestSummary_nullArgs_returnsNull() {
        assertThat(aiApi.findLatestSummary(null, 200L)).isNull();
        assertThat(aiApi.findLatestSummary(100L, null)).isNull();
    }

    private AiEnhanceResultEntity result(String resultJson) {
        AiEnhanceResultEntity entity = new AiEnhanceResultEntity();
        entity.setWorkspaceId(100L);
        entity.setKnowledgeId(200L);
        entity.setScene("SUMMARY");
        entity.setResultJson(resultJson);
        return entity;
    }
}
