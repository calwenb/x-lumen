package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.FeedbackEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 读者纠错数据访问（eng_feedback，F-1001）：仅 publishing 模块使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<FeedbackEntity> {
}
