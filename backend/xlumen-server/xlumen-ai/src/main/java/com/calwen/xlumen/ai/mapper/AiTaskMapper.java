package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务数据访问（ai_task，F-1302）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTaskEntity> {
}
