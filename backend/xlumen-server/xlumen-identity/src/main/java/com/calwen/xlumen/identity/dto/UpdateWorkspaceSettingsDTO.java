package com.calwen.xlumen.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新工作空间设置入参（F-1201）：PUT 全量更新简介与强制审核开关。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkspaceSettingsDTO {

    /** 空间简介（可空，空串表示清空）。 */
    @Size(max = 500, message = "空间简介不能超过 500 字符")
    private String intro;

    /** 强制审核开关（D9）：true 开启 false 关闭。 */
    @NotNull(message = "强制审核开关不能为空")
    private Boolean forceReview;
}
