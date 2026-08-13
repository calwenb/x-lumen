package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核驳回入参（F-0903）：驳回三要素（原因/位置/期望）必填 + 版本号乐观锁校验。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectDTO {

    /** 文章版本号（与审核记录快照版本一致）。 */
    @NotNull(message = "版本号不能为空")
    private Long version;

    /** 驳回原因。 */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因不能超过 500 字符")
    private String reason;

    /** 驳回位置。 */
    @NotBlank(message = "驳回位置不能为空")
    @Size(max = 200, message = "驳回位置不能超过 200 字符")
    private String position;

    /** 驳回期望。 */
    @NotBlank(message = "驳回期望不能为空")
    @Size(max = 500, message = "驳回期望不能超过 500 字符")
    private String expectation;
}
