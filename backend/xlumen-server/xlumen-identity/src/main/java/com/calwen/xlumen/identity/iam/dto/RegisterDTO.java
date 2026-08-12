package com.calwen.xlumen.identity.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册入参（F-0101）：注册成功即建空间（决策 D9），注册失败统一提示不暴露账号是否存在（PRODUCT §10 防枚举）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record RegisterDTO(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度需为 3~32 个字符")
        @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$", message = "用户名只能包含字母、数字、下划线或中文")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需为 8~64 个字符")
        String password,

        @Pattern(regexp = "^$|^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$", message = "邮箱格式不正确")
        String email
) {
}
