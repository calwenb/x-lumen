package com.calwen.xlumen.content.api;

import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;

import java.util.List;

/**
 * 内容模块对外接口（BACKEND.md §5.2）：公开读能力供 publishing 模块编排（博客前台公开读）。
 * 只暴露已发布且公开的知识（F-0307：私有不进公开列表与搜索）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface ContentApi {

    /**
     * 分页查询公开知识（F-0201/F-0202）：支持关键词（标题/摘要 LIKE）与分类/标签组合筛选。
     *
     * @param workspaceId 工作空间 ID
     * @param query       查询参数（关键词/分类/标签/分页）
     * @return 公开知识分页
     */
    ContentPageResult<PublishedKnowledgeDTO> listPublished(Long workspaceId, KnowledgeQueryDTO query);

    /**
     * 查询公开知识详情（F-0201）：仅已发布且公开的知识；不存在返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @return 详情或 null
     */
    KnowledgeDetailDTO getPublished(Long workspaceId, Long knowledgeId);

    /**
     * 分类聚合（F-0202）：返回有公开知识的分类及数量，按数量降序。
     *
     * @param workspaceId 工作空间 ID
     * @return 分类列表
     */
    List<CategoryCountDTO> listCategories(Long workspaceId);

    /**
     * 标签聚合（F-0202）：JSON_TABLE 展开标签统计，按数量降序。
     *
     * @param workspaceId 工作空间 ID
     * @return 标签列表
     */
    List<CategoryCountDTO> listTags(Long workspaceId);

    /**
     * 阅读量自增（F-0203）：publishing 侧 Redis 防刷通过后调用。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @return 是否自增成功（知识不存在或跨空间返回 false）
     */
    boolean incrementViewCount(Long workspaceId, Long knowledgeId);

    /**
     * 查询编辑态知识（含草稿/私有，M04）：供 publishing 审核读取正文（M10）使用；不存在或跨空间返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @return 编辑态知识或 null
     */
    EditorKnowledgeDTO getEditorKnowledge(Long workspaceId, Long knowledgeId);

    /**
     * 发布/状态迁移（M10，F-0901/F-0905）：publishing 通过本接口迁移知识状态与发布信息，
     * 版本乐观锁校验，不一致返回 false（由调用方抛 409）。
     *
     * @param workspaceId 工作空间 ID
     * @param dto         发布入参（知识 ID/期望版本/目标状态/可见性/发布时间）
     * @return 是否迁移成功
     */
    boolean publishKnowledge(Long workspaceId, KnowledgePublishDTO dto);
}
