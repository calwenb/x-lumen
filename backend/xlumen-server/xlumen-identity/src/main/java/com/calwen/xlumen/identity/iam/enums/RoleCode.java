package com.calwen.xlumen.identity.iam.enums;

/**
 * 角色编码（F-0103，BACKEND.md §8.1）：与 iam_role.role_code 一一对应。
 * 团队角色（ADMIN/EDITOR/AUTHOR）V2 团队模式启用（决策 D9），定义先入库。
 *
 * @author calwen
 * @date 2026/8/12
 */
public enum RoleCode {

    /** 空间所有者：注册即建空间默认角色。 */
    OWNER,
    /** 管理员（V2 团队模式启用）。 */
    ADMIN,
    /** 编辑（V2 团队模式启用）。 */
    EDITOR,
    /** 作者（V2 团队模式启用）。 */
    AUTHOR,
    /** 访客：公开博客访问者。 */
    VISITOR;

    /**
     * 判断字符串是否为合法角色编码。
     *
     * @param code 角色编码
     * @return 是否合法
     */
    public static boolean isValid(String code) {
        for (RoleCode roleCode : values()) {
            if (roleCode.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
