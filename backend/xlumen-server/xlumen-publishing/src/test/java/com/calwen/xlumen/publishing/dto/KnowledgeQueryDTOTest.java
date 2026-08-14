package com.calwen.xlumen.publishing.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页基类继承行为（BACKEND.md §5.1）：子类 @SuperBuilder 需覆盖继承字段，
 * 未显式设置时分页默认值取自基类 {@link com.calwen.xlumen.common.dto.PageQueryDTO}。
 *
 * @author calwen
 * @date 2026/8/14
 */
class KnowledgeQueryDTOTest {

    @Test
    void builderInheritsPageDefaults() {
        KnowledgeQueryDTO query = KnowledgeQueryDTO.builder().keyword("ai").build();

        assertThat(query.getKeyword()).isEqualTo("ai");
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);
    }

    @Test
    void builderOverridesPageFields() {
        KnowledgeQueryDTO query = KnowledgeQueryDTO.builder().pageNo(3).pageSize(50).build();

        assertThat(query.getPageNo()).isEqualTo(3);
        assertThat(query.getPageSize()).isEqualTo(50);
    }

    @Test
    void noArgsConstructorKeepsDefaults() {
        KnowledgeQueryDTO query = new KnowledgeQueryDTO();

        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);
    }

    @Test
    void setterOverridesDefaults() {
        KnowledgeQueryDTO query = new KnowledgeQueryDTO();
        query.setPageSize(30);

        assertThat(query.getPageSize()).isEqualTo(30);
    }
}
