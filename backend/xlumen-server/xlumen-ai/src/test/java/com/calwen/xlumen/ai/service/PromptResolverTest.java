package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.prompt.PromptTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景提示词解析单测：配置优先、缺省回退 PromptTemplates；写作多槽位 JSON 解析。
 *
 * @author calwen
 * @date 2026/8/26
 */
class PromptResolverTest {

    private SceneConfigService sceneConfigService;
    private PromptResolver promptResolver;

    @BeforeEach
    void setUp() {
        sceneConfigService = mock(SceneConfigService.class);
        when(sceneConfigService.resolve(anyLong(), any())).thenReturn(SceneModel.builder().build());
        promptResolver = new PromptResolver(sceneConfigService);
    }

    @Test
    void resolve_noConfig_returnsTemplateDefault() {
        assertThat(promptResolver.resolve(1L, AiScene.QA)).isEqualTo(PromptTemplates.QA_AGENT);
        assertThat(promptResolver.resolve(1L, AiScene.REVIEWER)).isEqualTo(PromptTemplates.REVIEWER_SYSTEM);
        assertThat(promptResolver.resolve(1L, AiScene.SUMMARY)).isEqualTo(PromptTemplates.SUMMARY);
        assertThat(promptResolver.resolve(1L, AiScene.SEO)).isEqualTo(PromptTemplates.SEO);
    }

    @Test
    void resolve_configuredPrompt_wins() {
        when(sceneConfigService.resolve(anyLong(), any()))
                .thenReturn(SceneModel.builder().prompt("自定义问答提示词").build());
        assertThat(promptResolver.resolve(1L, AiScene.QA)).isEqualTo("自定义问答提示词");
    }

    @Test
    void resolveWriting_slotJson_usesSlot() {
        when(sceneConfigService.resolve(anyLong(), any()))
                .thenReturn(SceneModel.builder()
                        .prompt("{\"outline\":\"自定义大纲\",\"revise\":\"自定义修订\"}")
                        .build());
        assertThat(promptResolver.resolveWriting(1L, "outline")).isEqualTo("自定义大纲");
        assertThat(promptResolver.resolveWriting(1L, "revise")).isEqualTo("自定义修订");
        // 缺失槽位回退常量
        assertThat(promptResolver.resolveWriting(1L, "chapter")).isEqualTo(PromptTemplates.WRITING_CHAPTER);
    }

    @Test
    void resolveWriting_nonJsonOrBlank_fallsBackToDefaults() {
        when(sceneConfigService.resolve(anyLong(), any()))
                .thenReturn(SceneModel.builder().prompt("非 JSON 配置").build());
        assertThat(promptResolver.resolveWriting(1L, "outline")).isEqualTo(PromptTemplates.WRITING_OUTLINE);
        assertThat(promptResolver.resolveWriting(1L, "self_review")).isEqualTo(PromptTemplates.WRITING_SELF_REVIEW);
        assertThat(promptResolver.resolveWriting(1L, "revise")).isEqualTo(PromptTemplates.WRITING_REVISE);
    }
}