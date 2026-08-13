package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论数据访问（eng_comment，F-0203）：仅 publishing 模块使用。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {
}
