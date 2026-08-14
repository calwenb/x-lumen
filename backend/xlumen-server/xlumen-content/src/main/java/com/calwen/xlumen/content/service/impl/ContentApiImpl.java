package com.calwen.xlumen.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import com.calwen.xlumen.content.entity.KnowledgeEntity;
import com.calwen.xlumen.content.mapper.KnowledgeMapper;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容模块对外接口实现（BACKEND.md §5.2：XxxApiImpl 放 service/impl/）：只暴露已发布且公开的知识（F-0307）。
 * 公开读数据源 cnt_knowledge 属 content 模块，publishing 通过本 Api 编排博客前台公开读。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class ContentApiImpl implements ContentApi {

    /** 状态：已发布（KnowledgeStatus.PUBLISHED，F-0901 八状态机）。 */
    private static final int STATUS_PUBLISHED = 6;
    /** 可见性：公开（F-0307）。 */
    private static final int VISIBILITY_PUBLIC = 1;

    @Resource
    private KnowledgeMapper knowledgeMapper;

    @Override
    public ContentPageResult<PublishedKnowledgeDTO> listPublished(Long workspaceId, KnowledgeQueryDTO query) {
        LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getStatus, STATUS_PUBLISHED)
                .eq(KnowledgeEntity::getVisibility, VISIBILITY_PUBLIC)
                .orderByDesc(KnowledgeEntity::getPublishedAt);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            // MVP 先 LIKE 后 ES（F-1305 V3 全文搜索）
            wrapper.and(w -> w.like(KnowledgeEntity::getTitle, query.getKeyword().trim())
                    .or().like(KnowledgeEntity::getSummary, query.getKeyword().trim()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            wrapper.eq(KnowledgeEntity::getCategory, query.getCategory().trim());
        }
        if (StrUtil.isNotBlank(query.getTag())) {
            // JSON 数组精确匹配：JSON_CONTAINS(tags, JSON_QUOTE(#{tag}))，参数化防注入
            wrapper.apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", query.getTag().trim());
        }
        Page<KnowledgeEntity> page = knowledgeMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        List<PublishedKnowledgeDTO> records = page.getRecords().stream()
                .map(a -> PublishedKnowledgeDTO.builder()
                        .id(a.getId()).title(a.getTitle()).summary(a.getSummary()).authorName(a.getAuthorName())
                        .category(a.getCategory()).tags(a.getTags()).viewCount(a.getViewCount())
                        .readMinutes(readMinutes(a.getContent())).publishedAt(a.getPublishedAt()).build())
                .toList();
        return ContentPageResult.<PublishedKnowledgeDTO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public KnowledgeDetailDTO getPublished(Long workspaceId, Long knowledgeId) {
        KnowledgeEntity knowledge = knowledgeMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getId, knowledgeId)
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getStatus, STATUS_PUBLISHED)
                .eq(KnowledgeEntity::getVisibility, VISIBILITY_PUBLIC));
        if (knowledge == null) {
            return null;
        }
        return KnowledgeDetailDTO.builder()
                .id(knowledge.getId()).title(knowledge.getTitle()).summary(knowledge.getSummary()).content(knowledge.getContent())
                .authorName(knowledge.getAuthorName()).category(knowledge.getCategory()).tags(knowledge.getTags())
                .viewCount(knowledge.getViewCount()).readMinutes(readMinutes(knowledge.getContent()))
                .publishedAt(knowledge.getPublishedAt()).updatedAt(knowledge.getUpdatedAt()).build();
    }

    @Override
    public List<CategoryCountDTO> listCategories(Long workspaceId) {
        return knowledgeMapper.selectCategoryCounts(workspaceId);
    }

    @Override
    public List<CategoryCountDTO> listTags(Long workspaceId) {
        return knowledgeMapper.selectTagCounts(workspaceId);
    }

    @Override
    public boolean incrementViewCount(Long workspaceId, Long knowledgeId) {
        return knowledgeMapper.incrementViewCount(knowledgeId, workspaceId) > 0;
    }

    @Override
    public EditorKnowledgeDTO getEditorKnowledge(Long workspaceId, Long knowledgeId) {
        KnowledgeEntity knowledge = knowledgeMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getId, knowledgeId)
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId));
        if (knowledge == null) {
            return null;
        }
        return EditorKnowledgeDTO.builder()
                .id(knowledge.getId()).workspaceId(knowledge.getWorkspaceId()).authorId(knowledge.getAuthorId())
                .title(knowledge.getTitle()).content(knowledge.getContent()).category(knowledge.getCategory())
                .tags(knowledge.getTags()).visibility(knowledge.getVisibility()).status(knowledge.getStatus())
                .version(knowledge.getVersion()).publishedAt(knowledge.getPublishedAt())
                .updatedAt(knowledge.getUpdatedAt()).build();
    }

    @Override
    public boolean publishKnowledge(Long workspaceId, KnowledgePublishDTO dto) {
        KnowledgeEntity knowledge = knowledgeMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getId, dto.getKnowledgeId())
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId));
        if (knowledge == null) {
            return false;
        }
        // 乐观锁：仅当版本一致才迁移（PRODUCT §6 禁止静默覆盖，冲突由调用方抛 409）
        knowledge.setStatus(dto.getTargetStatus());
        if (dto.getVisibility() != null) {
            knowledge.setVisibility(dto.getVisibility());
        }
        if (dto.getPublishedAt() != null) {
            knowledge.setPublishedAt(dto.getPublishedAt());
        }
        knowledge.setVersion(dto.getExpectedVersion());
        return knowledgeMapper.updateById(knowledge) > 0;
    }

    /** 阅读时间估算（分钟）：按 400 字/分钟，最少 1 分钟（B01/B02 卡片展示）。 */
    private int readMinutes(String content) {
        if (content == null) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(content.replaceAll("\\s+", "").length() / 400.0));
    }
}
