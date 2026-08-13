package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话消息数据访问（chat_message，F-0701/F-0702）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
