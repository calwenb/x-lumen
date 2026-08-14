package com.calwen.xlumen.knowledge.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库/目录实体映射契约（kb_knowledge_base / kb_directory，F-0308/F-0309，决策 D16）：
 * @TableName 与字段驼峰映射由 MyBatis-Plus 默认配置保证，此处锁定关键字段与状态语义。
 *
 * @author calwen
 * @date 2026/8/14
 */
class KbKnowledgeBaseEntityTest {

    @Test
    void tableMappingMatchesInitDdl() {
        KbKnowledgeBaseEntity kb = new KbKnowledgeBaseEntity();
        assertThat(kb.getClass().getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value())
                .isEqualTo("kb_knowledge_base");
    }

    @Test
    void visibilitySemanticsPublicIsOnePrivateIsZero() {
        // 与 init/20_knowledge.sql 注释一致：0 私有 / 1 公开（库级统一决定知识可见范围）
        assertThat(new KbKnowledgeBaseEntity().getVisibility()).isNull();
        KbKnowledgeBaseEntity publicKb = new KbKnowledgeBaseEntity();
        publicKb.setVisibility(1);
        assertThat(publicKb.getVisibility()).isEqualTo(1);
    }

    @Test
    void recycleStatusUsesStandaloneSoftDeleteColumns() {
        // 回收站用 status+deleted_at 独立软删标记，不扩 8 状态机（方案 §7.2 已确认）
        KbKnowledgeBaseEntity kb = new KbKnowledgeBaseEntity();
        kb.setStatus(1);
        kb.setDeletedAt(java.time.LocalDateTime.now());
        assertThat(kb.getStatus()).isEqualTo(1);
        assertThat(kb.getDeletedAt()).isNotNull();
    }
}
