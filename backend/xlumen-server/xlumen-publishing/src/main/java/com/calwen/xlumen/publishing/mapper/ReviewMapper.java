package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核记录数据访问（pub_review，F-0902/F-0903）：仅 publishing 模块使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface ReviewMapper extends BaseMapper<ReviewEntity> {
}
