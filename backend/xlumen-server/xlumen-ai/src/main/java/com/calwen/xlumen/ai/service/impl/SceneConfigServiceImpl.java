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

import java.util.List;

/**
 * 场景模型配置服务实现（F-0502）：ai_scene_config 表优先，无则回退 AiProperties 默认模型；
 * 密钥不入表（决策 D8），默认供应商为 BAILIAN。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class SceneConfigServiceImpl implements SceneConfigService {

    /** 默认供应商：BAILIAN（config/.env 提供默认密钥）。 */
    private static final String DEFAULT_PROVIDER = "BAILIAN";

    private final AiSceneConfigMapper sceneConfigMapper;
    private final AiProperties aiProperties;

    public SceneConfigServiceImpl(AiSceneConfigMapper sceneConfigMapper, AiProperties aiProperties) {
        this.sceneConfigMapper = sceneConfigMapper;
        this.aiProperties = aiProperties;
    }

    @Override
    public SceneModel resolve(Long workspaceId, AiScene scene) {
        if (workspaceId != null) {
            AiSceneConfigEntity config = sceneConfigMapper.selectOne(new LambdaQueryWrapper<AiSceneConfigEntity>()
                    .eq(AiSceneConfigEntity::getWorkspaceId, workspaceId)
                    .eq(AiSceneConfigEntity::getScene, scene.name())
                    .last("LIMIT 1"));
            if (config != null) {
                return SceneModel.builder()
                        .providerName(config.getProvider())
                        .model(config.getModel())
                        .paramsJson(config.getParamsJson())
                        .build();
            }
        }
        return SceneModel.builder()
                .providerName(DEFAULT_PROVIDER)
                .model(defaultModel(scene))
                .build();
    }

    @Override
    public List<ModelConfigVO> list(Long workspaceId) {
        List<AiSceneConfigEntity> list = sceneConfigMapper.selectList(new LambdaQueryWrapper<AiSceneConfigEntity>()
                .eq(AiSceneConfigEntity::getWorkspaceId, workspaceId)
                .orderByAsc(AiSceneConfigEntity::getScene));
        return list.stream()
                .map(e -> ModelConfigVO.builder()
                        .scene(e.getScene())
                        .provider(e.getProvider())
                        .model(e.getModel())
                        .paramsJson(e.getParamsJson())
                        .build())
                .toList();
    }

    @Override
    public void update(Long workspaceId, AiScene scene, String provider, String model, String paramsJson) {
        AiSceneConfigEntity existing = sceneConfigMapper.selectOne(new LambdaQueryWrapper<AiSceneConfigEntity>()
                .eq(AiSceneConfigEntity::getWorkspaceId, workspaceId)
                .eq(AiSceneConfigEntity::getScene, scene.name())
                .last("LIMIT 1"));
        AiSceneConfigEntity entity = existing == null ? new AiSceneConfigEntity() : existing;
        entity.setWorkspaceId(workspaceId);
        entity.setScene(scene.name());
        entity.setProvider(provider.toUpperCase());
        entity.setModel(model);
        entity.setParamsJson(paramsJson);
        if (entity.getId() == null) {
            sceneConfigMapper.insert(entity);
        } else {
            sceneConfigMapper.updateById(entity);
        }
    }

    /** 场景默认模型：SEO 复用摘要模型兜底。 */
    private String defaultModel(AiScene scene) {
        return switch (scene) {
            case WRITING -> aiProperties.getBailianModelWriting();
            case REVIEWER -> aiProperties.getBailianModelReviewer();
            case QA -> aiProperties.getBailianModelQa();
            case SUMMARY -> aiProperties.getBailianModelSummary();
            case SEO -> aiProperties.getBailianModelSummary();
            case EMBEDDING -> aiProperties.getBailianModelEmbedding();
        };
    }
}
