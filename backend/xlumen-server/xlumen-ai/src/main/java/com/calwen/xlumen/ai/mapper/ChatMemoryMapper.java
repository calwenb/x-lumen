package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.ChatMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话长期记忆数据访问（chat_memory）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {
}