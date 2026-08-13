package com.calwen.xlumen.content.api;

import com.calwen.xlumen.content.api.dto.ArticleDetailDTO;
import com.calwen.xlumen.content.api.dto.ArticleQueryDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.PublishedArticleDTO;

import java.util.List;

/**
 * 内容模块对外接口（BACKEND.md §5.2）：公开读能力供 publishing 模块编排（博客前台公开读）。
 * 只暴露已发布且公开的文章（F-0307：私有不进公开列表与搜索）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface ContentApi {

    /**
     * 分页查询公开文章（F-0201/F-0202）：支持关键词（标题/摘要 LIKE）与分类/标签组合筛选。
     *
     * @param workspaceId 工作空间 ID
     * @param query       查询参数（关键词/分类/标签/分页）
     * @return 公开文章分页
     */
    ContentPageResult<PublishedArticleDTO> listPublished(Long workspaceId, ArticleQueryDTO query);

    /**
     * 查询公开文章详情（F-0201）：仅已发布且公开的文章；不存在返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     * @return 详情或 null
     */
    ArticleDetailDTO getPublished(Long workspaceId, Long articleId);

    /**
     * 分类聚合（F-0202）：返回有公开文章的分类及数量，按数量降序。
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
     * @param articleId   文章 ID
     * @return 是否自增成功（文章不存在或跨空间返回 false）
     */
    boolean incrementViewCount(Long workspaceId, Long articleId);
}
