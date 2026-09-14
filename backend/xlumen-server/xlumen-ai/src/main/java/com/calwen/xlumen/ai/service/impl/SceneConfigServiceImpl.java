package com.calwen.xlumen.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiSceneConfigEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.AiSceneConfigMapper;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.vo.ModelConfigVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场景模型配置服务实现：ai_scene_config 表优先，无则回退 AiProperties 默认模型；
 * 密钥不入表（决策 D8），默认供应商为 BAILIAN。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class SceneConfigServiceImpl implements SceneConfigService {

    /** 默认供应商：BAILIAN（各环境 profile 提供默认密钥）。 */
    private static final String DEFAULT_PROVIDER = "BAILIAN";

    private final AiSceneConfigMapper sceneConfigMapper;
    private final AiProperties aiProperties;

    public SceneConfigServiceImpl(AiSceneConfigMapper sceneConfigMapper, AiProperties aiProperties) {
        this.sceneConfigMapper = sceneConfigMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public SceneModel resolve(Long workspaceId, AiScene scene) {
        AiSceneConfigEntity config = findOverride(workspaceId, scene);
        return config == null ? envDefault(scene) : toSceneModel(config);
    }

    @Override
    public List<ModelConfigVO> list(Long workspaceId) {
        Map<String, AiSceneConfigEntity> overrides = sceneConfigMapper.selectList(
                        new LambdaQueryWrapper<AiSceneConfigEntity>()
                                .eq(AiSceneConfigEntity::getWorkspaceId, workspaceId))
                .stream()
                .collect(Collectors.toMap(AiSceneConfigEntity::getScene, e -> e, (a, b) -> a));
        List<ModelConfigVO> result = new ArrayList<>(AiScene.values().length);
        for (AiScene scene : AiScene.values()) {
            result.add(toVo(scene, overrides.get(scene.name())));
        }
        return result;
    }

    @Override
    public ModelConfigVO get(Long workspaceId, AiScene scene) {
        return toVo(scene, findOverride(workspaceId, scene));
    }

    @Override
    public void update(Long workspaceId, AiScene scene, String provider, String model,
                       String paramsJson, String prompt, Integer dailyQuota) {
        AiSceneConfigEntity existing = findOverride(workspaceId, scene);
        AiSceneConfigEntity entity = existing == null ? new AiSceneConfigEntity() : existing;
        entity.setWorkspaceId(workspaceId);
        entity.setScene(scene.name());
        entity.setProvider(provider.toUpperCase());
        entity.setModel(model);
        entity.setParamsJson(paramsJson);
        if (prompt != null) {
            entity.setPrompt(prompt);
        }
        if (dailyQuota != null) {
            entity.setDailyQuota(dailyQuota);
        }
        if (entity.getId() == null) {
            sceneConfigMapper.insert(entity);
        } else {
            sceneConfigMapper.updateById(entity);
        }
    }

    /** 查询工作空间的场景覆盖行（无工作空间上下文或无记录时为 null）。 */
    private AiSceneConfigEntity findOverride(Long workspaceId, AiScene scene) {
        if (workspaceId == null) {
            return null;
        }
        return sceneConfigMapper.selectOne(new LambdaQueryWrapper<AiSceneConfigEntity>()
                .eq(AiSceneConfigEntity::getWorkspaceId, workspaceId)
                .eq(AiSceneConfigEntity::getScene, scene.name())
                .last("LIMIT 1"));
    }

    /** 组装管理面视图：覆盖行存在时生效值取覆盖行，否则与运行时一致地取环境默认。 */
    private ModelConfigVO toVo(AiScene scene, AiSceneConfigEntity override) {
        if (override != null) {
            return ModelConfigVO.builder()
                    .scene(scene.name())
                    .provider(override.getProvider())
                    .model(override.getModel())
                    .paramsJson(override.getParamsJson())
                    .prompt(override.getPrompt())
                    .dailyQuota(override.getDailyQuota())
                    .updatedAt(override.getUpdatedAt())
                    .effectiveProvider(override.getProvider())
                    .effectiveModel(override.getModel())
                    .source(ModelConfigVO.SOURCE_DB)
                    .build();
        }
        SceneModel fallback = envDefault(scene);
        return ModelConfigVO.builder()
                .scene(scene.name())
                .dailyQuota(0)
                .effectiveProvider(fallback.getProviderName())
                .effectiveModel(fallback.getModel())
                .source(ModelConfigVO.SOURCE_ENV)
                .build();
    }

    /** 覆盖行 → 场景模型。 */
    private SceneModel toSceneModel(AiSceneConfigEntity config) {
        return SceneModel.builder()
                .providerName(config.getProvider())
                .model(config.getModel())
                .paramsJson(config.getParamsJson())
                .prompt(config.getPrompt())
                .dailyQuota(config.getDailyQuota())
                .build();
    }

    /** 环境默认场景模型（各环境 profile 的 xlumen.bailian/deepseek.model-* 提供）。 */
    private SceneModel envDefault(AiScene scene) {
        return SceneModel.builder()
                .providerName(DEFAULT_PROVIDER)
                .model(defaultModel(scene))
                .build();
    }

    /** 场景默认模型：SEO 复用摘要模型兜底。 */
    private String defaultModel(AiScene scene) {
        return switch (scene) {
            case WRITING -> aiProperties.getBailianModelWriting();
            case REVIEWER -> aiProperties.getBailianModelReviewer();
            case QA -> aiProperties.getBailianModelQa();
            case SUMMARY -> aiProperties.getBailianModelSummary();
            case SEO -> aiProperties.getBailianModelSummary();
        };
    }
}
