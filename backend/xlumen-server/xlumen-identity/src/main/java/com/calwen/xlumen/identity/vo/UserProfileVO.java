package com.calwen.xlumen.identity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户资料（F-0101）：登录/注册成功后随令牌返回，用于前端会话快照（FRONTEND.md §7 accept()）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {

    /** 用户 ID。 */
    private Long userId;

    /** 用户名。 */
    private String username;

    /** 邮箱。 */
    private String email;

    /** 角色编码列表。 */
    private List<String> roles;
}
