package com.calwen.xlumen.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.knowledge.entity.KbKnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库数据访问（kb_knowledge_base，F-0308）：knowledge 模块承载库 CRUD 与可见性切换。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Mapper
public interface KbKnowledgeBaseMapper extends BaseMapper<KbKnowledgeBaseEntity> {
}
