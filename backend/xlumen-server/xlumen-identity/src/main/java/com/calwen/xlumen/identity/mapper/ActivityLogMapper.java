package com.calwen.xlumen.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.identity.entity.ActivityLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志数据访问（plt_activity_log，F-1202）：只增不改，仅 INSERT 与分页查询。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Mapper
public interface ActivityLogMapper extends BaseMapper<ActivityLogEntity> {
}
