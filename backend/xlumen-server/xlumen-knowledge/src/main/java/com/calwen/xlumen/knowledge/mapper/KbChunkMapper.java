package com.calwen.xlumen.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.knowledge.entity.KbChunkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 切片元数据数据访问（kb_chunk，F-0402/F-0405）：仅知识模块内部使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunkEntity> {
}
