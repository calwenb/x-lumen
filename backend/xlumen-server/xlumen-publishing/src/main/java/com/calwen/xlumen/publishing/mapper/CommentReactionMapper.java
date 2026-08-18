package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.CommentReactionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论反应数据访问（eng_comment_reaction，F-0213）：仅 publishing 模块使用。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Mapper
public interface CommentReactionMapper extends BaseMapper<CommentReactionEntity> {
}
