package com.calwen.xlumen.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识数据访问（cnt_knowledge，F-0201）：仅 content 模块内部使用（BACKEND.md §5.1）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {

    /**
     * 阅读量自增（F-0203）：独立 SQL 避免读改写竞态（BACKEND.md §11 并发）。
     *
     * @param id          知识 ID
     * @param workspaceId 工作空间 ID（防跨空间越权更新）
     * @return 影响行数
     */
    @Update("UPDATE cnt_knowledge SET view_count = view_count + 1 WHERE id = #{id} AND workspace_id = #{workspaceId}")
    int incrementViewCount(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    /**
     * 从回收站恢复（F-0305 软删）：仅清除回收站标记并留空删除时间，内容归属（kb_id/directory_id）不动；
     * 目录/知识库已被彻底删除等冲突校验由 knowledge 模块回收站服务统一处理（content 不依赖 knowledge）。
     *
     * @param id          知识 ID
     * @param workspaceId 工作空间 ID（防跨空间越权恢复）
     * @return 影响行数（0=不存在或已非回收站状态）
     */
    @Update("UPDATE cnt_knowledge SET recycle_status = 0, deleted_at = NULL "
            + "WHERE id = #{id} AND workspace_id = #{workspaceId}")
    int restore(@Param("id") Long id, @Param("workspaceId") Long workspaceId);

    /**
     * 标签聚合（F-0202）：JSON_TABLE 展开 tags 数组统计，按数量降序；仅统计已发布且不在回收站的知识
     * （KB-3 起跨空间全平台统计，workspaceId 可空=全平台，V2 按可见库细化）。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间全平台）
     * @return 标签统计列表
     */
    @Select("<script>SELECT t.tag AS name, COUNT(*) AS count FROM cnt_knowledge, "
            + "JSON_TABLE(tags, '$[*]' COLUMNS (tag VARCHAR(64) PATH '$')) AS t "
            + "WHERE status = 6 AND recycle_status = 0 "
            + "<if test='workspaceId != null'>AND workspace_id = #{workspaceId} </if>"
            + "GROUP BY t.tag ORDER BY count DESC, t.tag</script>")
    List<CategoryCountDTO> selectTagCounts(@Param("workspaceId") Long workspaceId);
}
