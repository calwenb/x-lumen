package com.calwen.xlumen.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.knowledge.entity.KbIndexVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 索引版本与活动指针数据访问（kb_index_version，F-0403）：仅知识模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface KbIndexVersionMapper extends BaseMapper<KbIndexVersionEntity> {
}
