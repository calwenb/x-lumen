package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiSceneConfigEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.AiSceneConfigMapper;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.vo.ModelConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景模型配置单测：管理面展示的「生效配置/来源」必须与运行时 resolve 的取值一致；
 * 未落库场景同样列出并标注环境默认。
 *
 * @author calwen
 * @date 2026/9/14
 */
class SceneConfigServiceImplTest {

    private static final Long WORKSPACE_ID = 100L;

    private AiSceneConfigMapper sceneConfigMapper;
    private SceneConfigServiceImpl sceneConfigService;

    @BeforeEach
    void setUp() {
        sceneConfigMapper = mock(AiSceneConfigMapper.class);
        AiProperties properties = new AiProperties();
        properties.setBailianModelWriting("qwen-writing");
        properties.setBailianModelReviewer("qwen-reviewer");
        properties.setBailianModelQa("qwen-qa");
        properties.setBailianModelSummary("qwen-summary");
        sceneConfigService = new SceneConfigServiceImpl(sceneConfigMapper, properties);
    }

    private AiSceneConfigEntity override(String scene, String model) {
        AiSceneConfigEntity entity = new AiSceneConfigEntity();
        entity.setWorkspaceId(WORKSPACE_ID);
        entity.setScene(scene);
        entity.setProvider("BAILIAN");
        entity.setModel(model);
        entity.setDailyQuota(0);
        entity.setUpdatedAt(LocalDateTime.of(2026, 9, 7, 22, 38));
        return entity;
    }

    @Test
    void list_withoutAnyOverride_listsEverySceneAsEnvDefault() {
        when(sceneConfigMapper.selectList(any())).thenReturn(List.of());

        List<ModelConfigVO> list = sceneConfigService.list(WORKSPACE_ID);

        assertThat(list).extracting(ModelConfigVO::getScene)
                .containsExactly("WRITING", "REVIEWER", "QA", "SUMMARY", "SEO");
        assertThat(list).allSatisfy(vo -> {
            assertThat(vo.getSource()).isEqualTo(ModelConfigVO.SOURCE_ENV);
            assertThat(vo.getProvider()).isNull();
            assertThat(vo.getModel()).isNull();
        });
        assertThat(list).filteredOn(vo -> vo.getScene().equals(AiScene.WRITING.name()))
                .singleElement()
                .satisfies(vo -> assertThat(vo.getEffectiveModel()).isEqualTo("qwen-writing"));
        // SEO 复用摘要默认模型。
        assertThat(list).filteredOn(vo -> vo.getScene().equals(AiScene.SEO.name()))
                .singleElement()
                .satisfies(vo -> assertThat(vo.getEffectiveModel()).isEqualTo("qwen-summary"));
    }

    @Test
    void list_withOverride_marksDbSourceAndShowsOverriddenValueAsEffective() {
        when(sceneConfigMapper.selectList(any()))
                .thenReturn(List.of(override(AiScene.WRITING.name(), "qwen-plus")));

        ModelConfigVO writing = sceneConfigService.list(WORKSPACE_ID).stream()
                .filter(vo -> vo.getScene().equals(AiScene.WRITING.name()))
                .findFirst()
                .orElseThrow();

        assertThat(writing.getSource()).isEqualTo(ModelConfigVO.SOURCE_DB);
        assertThat(writing.getProvider()).isEqualTo("BAILIAN");
        assertThat(writing.getModel()).isEqualTo("qwen-plus");
        assertThat(writing.getEffectiveProvider()).isEqualTo("BAILIAN");
        assertThat(writing.getEffectiveModel()).isEqualTo("qwen-plus");
        assertThat(writing.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 7, 22, 38));
    }

    @Test
    void effectiveValue_equalsRuntimeResolveForBothBranches() {
        when(sceneConfigMapper.selectOne(any())).thenReturn(override(AiScene.QA.name(), "qwen-plus"));
        when(sceneConfigMapper.selectList(any()))
                .thenReturn(List.of(override(AiScene.QA.name(), "qwen-plus")));

        SceneModel resolved = sceneConfigService.resolve(WORKSPACE_ID, AiScene.QA);
        ModelConfigVO vo = sceneConfigService.list(WORKSPACE_ID).stream()
                .filter(item -> item.getScene().equals(AiScene.QA.name()))
                .findFirst()
                .orElseThrow();

        assertThat(vo.getEffectiveProvider()).isEqualTo(resolved.getProviderName());
        assertThat(vo.getEffectiveModel()).isEqualTo(resolved.getModel());
    }

    @Test
    void get_withoutOverride_fallsBackToEnvDefault() {
        when(sceneConfigMapper.selectOne(any())).thenReturn(null);

        ModelConfigVO vo = sceneConfigService.get(WORKSPACE_ID, AiScene.REVIEWER);

        assertThat(vo.getSource()).isEqualTo(ModelConfigVO.SOURCE_ENV);
        assertThat(vo.getEffectiveModel()).isEqualTo("qwen-reviewer");
    }
}
