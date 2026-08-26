package com.calwen.xlumen.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 忘记密码：验证码重置入参。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordDTO {

    /** 注册邮箱。 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 6 位数字验证码。 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
    private String code;

    /** 新密码。 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8~64 位")
    private String newPassword;
}