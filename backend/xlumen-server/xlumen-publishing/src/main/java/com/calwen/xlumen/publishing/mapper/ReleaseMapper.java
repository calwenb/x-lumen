package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.ReleaseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布记录数据访问（pub_release，F-0904/F-0905）：仅 publishing 模块使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface ReleaseMapper extends BaseMapper<ReleaseEntity> {
}
