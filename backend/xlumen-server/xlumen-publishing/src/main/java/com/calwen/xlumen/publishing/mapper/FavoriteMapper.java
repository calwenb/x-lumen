package com.calwen.xlumen.publishing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.publishing.entity.FavoriteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识收藏数据访问（eng_favorite，F-0212）：仅 publishing 模块使用。
 *
 * @author calwen
 * @date 2026/8/18
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<FavoriteEntity> {
}
