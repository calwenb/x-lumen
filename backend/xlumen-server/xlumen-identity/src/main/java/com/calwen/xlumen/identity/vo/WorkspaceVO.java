package com.calwen.xlumen.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作空间视图（F-0102）：MVP 注册即建空间单空间使用（决策 D9），切换与成员邀请 V2 启用。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceVO {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 空间名称。 */
    private String name;

    /** 空间标识。 */
    private String slug;

    /** 当前用户角色编码。 */
    private String roleCode;
}
