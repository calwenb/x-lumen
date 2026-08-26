package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.AiCallLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用追踪 Mapper。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLogEntity> {
}