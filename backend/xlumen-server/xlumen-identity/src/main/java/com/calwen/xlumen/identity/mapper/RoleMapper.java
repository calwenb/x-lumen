package com.calwen.xlumen.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.identity.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色数据访问（iam_role，F-0103）：仅身份模块内部使用。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
}
