package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.vo.ModelConfigVO;

import java.util.List;

/**
 * 场景模型配置服务（F-0502）：表优先、AiProperties 默认回退；管理面增改查（密钥永不返回）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface SceneConfigService {

    /**
     * 解析场景模型：先查 ai_scene_config（workspace_id + scene），无则回退 AiProperties 默认。
     *
     * @param workspaceId 工作空间 ID（可空）
     * @param scene       场景
     * @return 场景模型
     */
    SceneModel resolve(Long workspaceId, AiScene scene);

    /**
     * 查询工作空间下的场景配置列表（密钥永不返回）。
     *
     * @param workspaceId 工作空间 ID
     * @return 配置列表
     */
    List<ModelConfigVO> list(Long workspaceId);

    /**
     * 新增或更新场景配置（provider/model/paramsJson，密钥不入表）。
     *
     * @param workspaceId 工作空间 ID
     * @param scene       场景
     * @param provider    供应商名
     * @param model       模型名
     * @param paramsJson  场景参数（可空）
     */
    void update(Long workspaceId, AiScene scene, String provider, String model, String paramsJson);
}
