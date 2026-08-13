package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 读者纠错入参（F-1001）：匿名可提交；ip 由 Controller 从请求中取回填（不来自请求体）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeedbackDTO {

    /** 纠错位置（可空）。 */
    @Size(max = 200, message = "纠错位置不能超过 200 字符")
    private String position;

    /** 问题描述。 */
    @NotBlank(message = "问题描述不能为空")
    @Size(max = 1000, message = "问题描述不能超过 1000 字符")
    private String problem;

    /** 证据/建议（可空）。 */
    @Size(max = 2000, message = "证据/建议不能超过 2000 字符")
    private String evidence;

    /** 提交者 IP（服务端从请求回填，用于限流，非请求体字段）。 */
    private String ip;
}
