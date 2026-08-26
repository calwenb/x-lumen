package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.ChangelogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点更新日志 Mapper。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Mapper
public interface ChangelogMapper extends BaseMapper<ChangelogEntity> {
}