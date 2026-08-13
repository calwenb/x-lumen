package com.calwen.xlumen.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.identity.entity.WorkspaceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间数据访问（iam_workspace，F-0102）：仅身份模块内部使用。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceEntity> {
}
