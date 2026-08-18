package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.entity.AiEnhanceResultEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.AiEnhanceResultMapper;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.vo.EnhanceResultVO;
import com.calwen.xlumen.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 摘要生成单元测试（F-0808）：generateAndStoreSummary 落库断言（mock ModelGateway，不调真实模型）。
 *
 * @author calwen
 * @date 2026/8/18
 */
class EnhanceServiceImplTest {

    @Mock
    private ModelGateway modelGateway;

    @Mock
    private AiEnhanceResultMapper enhanceResultMapper;

    private EnhanceServiceImpl enhanceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        enhanceService = new EnhanceServiceImpl(modelGateway, enhanceResultMapper);
    }

    @Test
    void generateAndStoreSummary_storesSceneSummaryWithKnowledgeId() {
        // mock 模型输出（带代码围栏，覆盖解析路径）
        when(modelGateway.chat(any(), eq(AiScene.SUMMARY), any()))
                .thenReturn("```json\n{\"summary\":\"这是一篇关于向量检索的摘要\"}\n```");

        EnhanceResultVO vo = enhanceService.generateAndStoreSummary(100L, 200L, "向量检索入门", "正文正文正文");

        ArgumentCaptor<AiEnhanceResultEntity> captor = ArgumentCaptor.forClass(AiEnhanceResultEntity.class);
        verify(enhanceResultMapper).insert(captor.capture());
        AiEnhanceResultEntity entity = captor.getValue();
        assertThat(entity.getWorkspaceId()).isEqualTo(100L);
        assertThat(entity.getKnowledgeId()).isEqualTo(200L);
        assertThat(entity.getScene()).isEqualTo("SUMMARY");
        assertThat(entity.getResultJson()).contains("这是一篇关于向量检索的摘要");
        assertThat(vo.getScene()).isEqualTo("SUMMARY");
        assertThat(vo.getKnowledgeId()).isEqualTo(200L);
    }

    @Test
    void generateAndStoreSummary_blankContent_throwsInvalidParam() {
        assertThatThrownBy(() -> enhanceService.generateAndStoreSummary(100L, 200L, "  ", ""))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待处理内容不能为空");
    }

    @Test
    void generateAndStoreSummary_modelOutputInvalid_throwsServiceUnavailable() {
        when(modelGateway.chat(any(), eq(AiScene.SUMMARY), any())).thenReturn("不是 JSON 的输出");

        // 模型调用失败/输出不合法由调用方决定降级：这里断言直接抛出
        assertThatThrownBy(() -> enhanceService.generateAndStoreSummary(100L, 200L, "标题", "正文"))
                .isInstanceOf(BizException.class);
    }
}
