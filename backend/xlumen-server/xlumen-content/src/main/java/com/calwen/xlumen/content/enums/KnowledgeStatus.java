package com.calwen.xlumen.content.enums;

/**
 * 知识状态枚举（F-0901 八状态机）：与 cnt_knowledge.status 一一对应，全仓唯一状态定义（PRODUCT.md §4）。
 * 构思→草稿→待审核→已通过→定时发布→已发布；已发布→更新中→新版本重新审核；可下架。
 * 流转规则由 publishing 模块审核/发布服务集中实现（BACKEND.md §22），此处只承载状态值语义。
 *
 * @author calwen
 * @date 2026/8/13
 */
public enum KnowledgeStatus {

    /** 构思：仅作者可见的初始想法。 */
    IDEA(1),
    /** 草稿：作者编辑中（F-0301/F-0302）。 */
    DRAFT(2),
    /** 待审核：已提交审核，等待双闸门审核（F-0902）。 */
    PENDING_REVIEW(3),
    /** 已通过：审核通过，可发布。 */
    APPROVED(4),
    /** 定时发布：已排程等待到期发布（F-0905 幂等执行）。 */
    SCHEDULED(5),
    /** 已发布：线上公开/私有版本，正文不可修改（PRODUCT §4）。 */
    PUBLISHED(6),
    /** 更新中：已发布知识的旧文更新新版本（V2 F-1105 闭环，MVP 预留）。 */
    UPDATING(7),
    /** 已下架：从线上撤回，可回滚重新发布（V2 F-0906）。 */
    UNPUBLISHED(8);

    /** 数据库存储值。 */
    private final int value;

    KnowledgeStatus(int value) {
        this.value = value;
    }

    /** @return 数据库存储值 */
    public int getValue() {
        return value;
    }

    /**
     * 按存储值解析状态。
     *
     * @param value 存储值
     * @return 状态枚举；非法值返回 null
     */
    public static KnowledgeStatus of(Integer value) {
        if (value == null) {
            return null;
        }
        for (KnowledgeStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }
}
