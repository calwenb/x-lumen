package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.AiSceneConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 场景模型配置数据访问（ai_scene_config，F-0502）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface AiSceneConfigMapper extends BaseMapper<AiSceneConfigEntity> {
}
