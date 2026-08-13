package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.ChatConversationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话数据访问（chat_conversation，F-0701）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationEntity> {
}
