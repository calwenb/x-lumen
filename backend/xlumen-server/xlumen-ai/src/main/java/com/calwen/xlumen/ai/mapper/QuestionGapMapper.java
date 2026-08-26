package com.calwen.xlumen.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.ai.entity.QuestionGapEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 问答知识缺口数据访问（ai_question_gap）：仅 ai 模块内部使用。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Mapper
public interface QuestionGapMapper extends BaseMapper<QuestionGapEntity> {
}