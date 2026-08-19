package com.calwen.xlumen.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.content.entity.KnowledgeVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识版本快照数据访问（cnt_knowledge_version，F-0303 历史版本）。
 *
 * @author calwen
 * @date 2026/8/19
 */
@Mapper
public interface KnowledgeVersionMapper extends BaseMapper<KnowledgeVersionEntity> {
}
