package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.ArticleCardVO;
import com.calwen.xlumen.publishing.dto.ArticleDetailVO;
import com.calwen.xlumen.publishing.dto.PageResult;

import java.util.List;

/**
 * 博客前台公开读服务（F-0201/F-0202，review/release 域）：编排 ContentApi 与 engagement 统计。
 * 工作空间取默认空间（MVP 单空间，决策 D9），私有/未发布文章不出现（F-0307 由 ContentApi 保证）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface PublicArticleService {

    /**
     * 分页查询公开文章（含互动统计）。
     *
     * @param keyword  关键词（可空）
     * @param category 分类（可空）
     * @param tag      标签（可空）
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @return 文章卡片分页
     */
    PageResult<ArticleCardVO> listArticles(String keyword, String category, String tag, long pageNo, long pageSize);

    /**
     * 文章详情（含互动统计与当前用户点赞状态）。
     *
     * @param articleId 文章 ID
     * @param userId    当前用户 ID（匿名为 null）
     * @return 详情；不存在抛 404
     */
    ArticleDetailVO getArticle(Long articleId, Long userId);

    /**
     * 阅读量防刷自增（F-0203）：同一访客 24 小时内只计一次（Redis 短期状态，决策 D6）。
     *
     * @param articleId  文章 ID
     * @param visitorKey 访客指纹（IP 等）
     * @return 是否本次计为新增阅读
     */
    boolean recordView(Long articleId, String visitorKey);

    /**
     * 分类聚合（F-0202）。
     *
     * @return 分类列表
     */
    List<CategoryCountDTO> listCategories();

    /**
     * 标签聚合（F-0202）。
     *
     * @return 标签列表
     */
    List<CategoryCountDTO> listTags();
}
