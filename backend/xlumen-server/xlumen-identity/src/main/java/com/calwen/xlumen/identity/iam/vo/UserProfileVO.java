package com.calwen.xlumen.identity.iam.vo;

import java.util.List;

/**
 * 用户资料（F-0101）：登录/注册成功后随令牌返回，用于前端会话快照（FRONTEND.md §7 accept()）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public record UserProfileVO(Long userId, String username, String email, List<String> roles) {
}
