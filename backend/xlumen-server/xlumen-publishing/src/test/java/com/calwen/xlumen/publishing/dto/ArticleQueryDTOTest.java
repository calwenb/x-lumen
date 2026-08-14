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
class ArticleQueryDTOTest {

    @Test
    void builderInheritsPageDefaults() {
        ArticleQueryDTO query = ArticleQueryDTO.builder().keyword("ai").build();

        assertThat(query.getKeyword()).isEqualTo("ai");
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);
    }

    @Test
    void builderOverridesPageFields() {
        ArticleQueryDTO query = ArticleQueryDTO.builder().pageNo(3).pageSize(50).build();

        assertThat(query.getPageNo()).isEqualTo(3);
        assertThat(query.getPageSize()).isEqualTo(50);
    }

    @Test
    void noArgsConstructorKeepsDefaults() {
        ArticleQueryDTO query = new ArticleQueryDTO();

        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(20);
    }

    @Test
    void setterOverridesDefaults() {
        ArticleQueryDTO query = new ArticleQueryDTO();
        query.setPageSize(30);

        assertThat(query.getPageSize()).isEqualTo(30);
    }
}
