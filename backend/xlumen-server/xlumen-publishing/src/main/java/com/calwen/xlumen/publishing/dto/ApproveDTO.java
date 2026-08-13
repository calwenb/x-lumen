package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核通过入参（F-0903）：携带文章版本号做乐观锁校验。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveDTO {

    /** 文章版本号（与审核记录快照版本一致，防止误通过旧版本）。 */
    @NotNull(message = "版本号不能为空")
    private Long version;
}
