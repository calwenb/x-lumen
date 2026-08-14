package com.calwen.xlumen.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.knowledge.entity.KbDirectoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 目录树数据访问（kb_directory，F-0309）：knowledge 模块承载多级目录 CRUD 与按名称排序查询。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Mapper
public interface KbDirectoryMapper extends BaseMapper<KbDirectoryEntity> {
}
