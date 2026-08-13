package com.calwen.xlumen.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.identity.entity.WorkspaceMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 空间成员数据访问（iam_workspace_member，F-0102/F-0103）：仅身份模块内部使用。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface WorkspaceMemberMapper extends BaseMapper<WorkspaceMemberEntity> {
}
