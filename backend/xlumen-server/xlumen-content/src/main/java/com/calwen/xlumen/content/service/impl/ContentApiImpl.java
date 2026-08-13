package com.calwen.xlumen.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ArticleDetailDTO;
import com.calwen.xlumen.content.api.dto.ArticleQueryDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.PublishedArticleDTO;
import com.calwen.xlumen.content.entity.ArticleEntity;
import com.calwen.xlumen.content.mapper.ArticleMapper;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容模块对外接口实现（BACKEND.md §5.2：XxxApiImpl 放 service/impl/）：只暴露已发布且公开的文章（F-0307）。
 * 公开读数据源 cnt_article 属 content 模块，publishing 通过本 Api 编排博客前台公开读。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class ContentApiImpl implements ContentApi {

    /** 状态：已发布（完整 8 状态机随 M10 细化）。 */
    private static final int STATUS_PUBLISHED = 2;
    /** 可见性：公开（F-0307）。 */
    private static final int VISIBILITY_PUBLIC = 1;

    @Resource
    private ArticleMapper articleMapper;

    @Override
    public ContentPageResult<PublishedArticleDTO> listPublished(Long workspaceId, ArticleQueryDTO query) {
        LambdaQueryWrapper<ArticleEntity> wrapper = new LambdaQueryWrapper<ArticleEntity>()
                .eq(ArticleEntity::getWorkspaceId, workspaceId)
                .eq(ArticleEntity::getStatus, STATUS_PUBLISHED)
                .eq(ArticleEntity::getVisibility, VISIBILITY_PUBLIC)
                .orderByDesc(ArticleEntity::getPublishedAt);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            // MVP 先 LIKE 后 ES（F-1305 V3 全文搜索）
            wrapper.and(w -> w.like(ArticleEntity::getTitle, query.getKeyword().trim())
                    .or().like(ArticleEntity::getSummary, query.getKeyword().trim()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            wrapper.eq(ArticleEntity::getCategory, query.getCategory().trim());
        }
        if (StrUtil.isNotBlank(query.getTag())) {
            // JSON 数组精确匹配：JSON_CONTAINS(tags, JSON_QUOTE(#{tag}))，参数化防注入
            wrapper.apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", query.getTag().trim());
        }
        Page<ArticleEntity> page = articleMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        List<PublishedArticleDTO> records = page.getRecords().stream()
                .map(a -> PublishedArticleDTO.builder()
                        .id(a.getId()).title(a.getTitle()).summary(a.getSummary()).authorName(a.getAuthorName())
                        .category(a.getCategory()).tags(a.getTags()).viewCount(a.getViewCount())
                        .readMinutes(readMinutes(a.getContent())).publishedAt(a.getPublishedAt()).build())
                .toList();
        return ContentPageResult.<PublishedArticleDTO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public ArticleDetailDTO getPublished(Long workspaceId, Long articleId) {
        ArticleEntity article = articleMapper.selectOne(new LambdaQueryWrapper<ArticleEntity>()
                .eq(ArticleEntity::getId, articleId)
                .eq(ArticleEntity::getWorkspaceId, workspaceId)
                .eq(ArticleEntity::getStatus, STATUS_PUBLISHED)
                .eq(ArticleEntity::getVisibility, VISIBILITY_PUBLIC));
        if (article == null) {
            return null;
        }
        return ArticleDetailDTO.builder()
                .id(article.getId()).title(article.getTitle()).summary(article.getSummary()).content(article.getContent())
                .authorName(article.getAuthorName()).category(article.getCategory()).tags(article.getTags())
                .viewCount(article.getViewCount()).readMinutes(readMinutes(article.getContent()))
                .publishedAt(article.getPublishedAt()).updatedAt(article.getUpdatedAt()).build();
    }

    @Override
    public List<CategoryCountDTO> listCategories(Long workspaceId) {
        return articleMapper.selectCategoryCounts(workspaceId);
    }

    @Override
    public List<CategoryCountDTO> listTags(Long workspaceId) {
        return articleMapper.selectTagCounts(workspaceId);
    }

    @Override
    public boolean incrementViewCount(Long workspaceId, Long articleId) {
        return articleMapper.incrementViewCount(articleId, workspaceId) > 0;
    }

    /** 阅读时间估算（分钟）：按 400 字/分钟，最少 1 分钟（B01/B02 卡片展示）。 */
    private int readMinutes(String content) {
        if (content == null) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(content.replaceAll("\\s+", "").length() / 400.0));
    }
}
