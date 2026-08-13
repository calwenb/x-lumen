package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.AiEnhanceResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 增值结果数据访问（ai_enhance_result，F-0801/F-0802）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface AiEnhanceResultMapper extends BaseMapper<AiEnhanceResultEntity> {
}
