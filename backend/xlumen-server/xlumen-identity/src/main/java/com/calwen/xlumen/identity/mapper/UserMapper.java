package com.calwen.xlumen.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.identity.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问（iam_user，F-0101）：仅身份模块内部使用（BACKEND.md §5.1）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
