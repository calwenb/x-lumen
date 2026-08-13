package com.calwen.xlumen.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作空间设置视图（F-1201）：含空间简介与强制审核开关（决策 D9）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSettingsVO {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 空间名称。 */
    private String name;

    /** 空间标识。 */
    private String slug;

    /** 空间简介（可空）。 */
    private String intro;

    /** 强制审核开关（D9）：true 开启 false 关闭。 */
    private Boolean forceReview;
}
