package com.calwen.xlumen.content.editor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.editor.entity.ArticleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章数据访问（cnt_article，F-0201）：仅 content 模块内部使用（BACKEND.md §5.1）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface ArticleMapper extends BaseMapper<ArticleEntity> {

    /**
     * 阅读量自增（F-0203）：独立 SQL 避免读改写竞态（BACKEND.md §11 并发）。
     *
     * @param id          文章 ID
     * @param workspaceId 工作空间 ID（防跨空间越权更新）
     * @return 影响行数
     */
    @Update("UPDATE cnt_article SET view_count = view_count + 1 WHERE id = #{id} AND workspace_id = #{workspaceId}")
    int incrementViewCount(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    /**
     * 分类聚合（F-0202）：仅统计已发布公开文章，按数量降序。
     *
     * @param workspaceId 工作空间 ID
     * @return 分类统计列表（name/count 与 record 构造器映射）
     */
    @Select("SELECT category AS name, COUNT(*) AS count FROM cnt_article "
            + "WHERE workspace_id = #{workspaceId} AND status = 2 AND visibility = 1 AND category <> '' "
            + "GROUP BY category ORDER BY count DESC, category")
    List<CategoryCountDTO> selectCategoryCounts(@Param("workspaceId") Long workspaceId);

    /**
     * 标签聚合（F-0202）：JSON_TABLE 展开 tags 数组统计，按数量降序。
     *
     * @param workspaceId 工作空间 ID
     * @return 标签统计列表
     */
    @Select("SELECT t.tag AS name, COUNT(*) AS count FROM cnt_article, "
            + "JSON_TABLE(tags, '$[*]' COLUMNS (tag VARCHAR(64) PATH '$')) AS t "
            + "WHERE workspace_id = #{workspaceId} AND status = 2 AND visibility = 1 "
            + "GROUP BY t.tag ORDER BY count DESC, t.tag")
    List<CategoryCountDTO> selectTagCounts(@Param("workspaceId") Long workspaceId);
}
