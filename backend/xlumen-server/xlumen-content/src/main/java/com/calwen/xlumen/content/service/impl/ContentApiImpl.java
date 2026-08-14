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
import java.util.Map;

/**
 * 内容模块对外接口实现（BACKEND.md §5.2：XxxApiImpl 放 service/impl/）：只暴露已发布、不在回收站、
 * 且所属知识库在可见集合内的知识（F-0307/F-0407）。公开读数据源 cnt_knowledge 属 content 模块，
 * publishing 通过本 Api 编排博客前台公开读；可见库集合由 publishing 按身份推导后经
 * {@link KnowledgeQueryDTO#getVisibleKbIds()} 传入（content 不依赖 knowledge 模块，无法自查库可见性）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class ContentApiImpl implements ContentApi {

    /** 状态：已发布（KnowledgeStatus.PUBLISHED，F-0901 八状态机）。 */
    private static final int STATUS_PUBLISHED = 6;
    /** 回收站状态：正常（F-0305 独立软删标记，回收站知识不进公开读）。 */
    private static final int RECYCLE_STATUS_NORMAL = 0;

    @Resource
    private KnowledgeMapper knowledgeMapper;

    @Override
    public ContentPageResult<PublishedKnowledgeDTO> listPublished(Long workspaceId, KnowledgeQueryDTO query) {
        List<Long> visibleKbIds = query.getVisibleKbIds();
        // 可见库集合由 publishing 按身份推导传入（F-0407：访客=全平台公开库，登录=+本人私有库）；
        // 为空 = 无任何可见库，直接返回空页（内容归属 kb_id 必填，越权指定库自然被 IN 条件排除）
        if (visibleKbIds == null || visibleKbIds.isEmpty()) {
            return ContentPageResult.<PublishedKnowledgeDTO>builder()
                    .total(0L).pageNo(query.getPageNo()).pageSize(query.getPageSize()).records(List.of()).build();
        }
        LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<KnowledgeEntity>()
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）：范围由可见库集合精确表达
                .eq(workspaceId != null, KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getStatus, STATUS_PUBLISHED)
                .eq(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
                .in(KnowledgeEntity::getKbId, visibleKbIds);
        if (query.getKbId() != null) {
            // 指定库时叠加可见集合条件：目标库不可见则结果为空（越权返回空，不报错）
            wrapper.eq(KnowledgeEntity::getKbId, query.getKbId());
        }
        if (query.getDirectoryId() != null) {
            wrapper.eq(KnowledgeEntity::getDirectoryId, query.getDirectoryId());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            // MVP 先 LIKE 后 ES（F-1305 V3 全文搜索）
            wrapper.and(w -> w.like(KnowledgeEntity::getTitle, query.getKeyword().trim())
                    .or().like(KnowledgeEntity::getSummary, query.getKeyword().trim()));
        }
        if (StrUtil.isNotBlank(query.getTag())) {
            // JSON 数组精确匹配：JSON_CONTAINS(tags, JSON_QUOTE(#{tag}))，参数化防注入
            wrapper.apply("JSON_CONTAINS(tags, JSON_QUOTE({0}))", query.getTag().trim());
        }
        // 排序（决策 D16）：未选目录按 updated_at 倒序；选中目录（库内浏览）按 created_at 正序
        if (query.getDirectoryId() != null) {
            wrapper.orderByAsc(KnowledgeEntity::getCreatedAt);
        } else {
            wrapper.orderByDesc(KnowledgeEntity::getUpdatedAt);
        }
        Page<KnowledgeEntity> page = knowledgeMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        List<PublishedKnowledgeDTO> records = page.getRecords().stream()
                .map(a -> PublishedKnowledgeDTO.builder()
                        .id(a.getId()).title(a.getTitle()).summary(a.getSummary()).authorName(a.getAuthorName())
                        // kbName 由 publishing 层按 kbId 查库名组装填充（content 不依赖 knowledge 模块，决策 D16）
                        .kbId(a.getKbId()).kbName(null).directoryId(a.getDirectoryId())
                        .tags(a.getTags()).viewCount(a.getViewCount())
                        .readMinutes(readMinutes(a.getContent())).publishedAt(a.getPublishedAt())
                        .updatedAt(a.getUpdatedAt()).build())
                .toList();
        return ContentPageResult.<PublishedKnowledgeDTO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public KnowledgeDetailDTO getPublished(Long workspaceId, Long knowledgeId, List<Long> visibleKbIds) {
        // 可见库集合为空 = 无任何可见库，直接返回 null（F-0407）
        if (visibleKbIds == null || visibleKbIds.isEmpty()) {
            return null;
        }
        KnowledgeEntity knowledge = knowledgeMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getId, knowledgeId)
                // workspaceId 可空=跨空间聚合（多用户公开读，D9 改写）：可见库集合精确表达范围
                .eq(workspaceId != null, KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getStatus, STATUS_PUBLISHED)
                .eq(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
                .in(KnowledgeEntity::getKbId, visibleKbIds));
        if (knowledge == null) {
            return null;
        }
        return KnowledgeDetailDTO.builder()
                .id(knowledge.getId()).title(knowledge.getTitle()).summary(knowledge.getSummary()).content(knowledge.getContent())
                .authorName(knowledge.getAuthorName())
                // kbName 由 publishing 层按 kbId 查库名组装填充（content 不依赖 knowledge 模块，决策 D16）
                .kbId(knowledge.getKbId()).kbName(null).directoryId(knowledge.getDirectoryId())
                .tags(knowledge.getTags())
                .viewCount(knowledge.getViewCount()).readMinutes(readMinutes(knowledge.getContent()))
                .publishedAt(knowledge.getPublishedAt()).updatedAt(knowledge.getUpdatedAt()).build();
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
                .title(knowledge.getTitle()).content(knowledge.getContent())
                .kbId(knowledge.getKbId()).directoryId(knowledge.getDirectoryId())
                .tags(knowledge.getTags()).status(knowledge.getStatus())
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
        if (dto.getKbId() != null) {
            // 发布时落库归属（决策 D16 单库单目录；null=沿用草稿期归属）
            knowledge.setKbId(dto.getKbId());
        }
        if (dto.getDirectoryId() != null) {
            knowledge.setDirectoryId(dto.getDirectoryId());
        }
        if (dto.getPublishedAt() != null) {
            knowledge.setPublishedAt(dto.getPublishedAt());
        }
        knowledge.setVersion(dto.getExpectedVersion());
        return knowledgeMapper.updateById(knowledge) > 0;
    }

    // ==================== KB-3 知识平台化契约实现（F-0305/F-0308/F-0309） ====================

    @Override
    public void softDeleteKnowledgeByKb(Long workspaceId, Long kbId) {
        // 幂等：库内无知识也成功；不存在/跨空间由 UPDATE 影响行数 0 自然跳过
        knowledgeMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<KnowledgeEntity>lambdaUpdate()
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getKbId, kbId)
                .eq(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
                .set(KnowledgeEntity::getRecycleStatus, 1)
                .set(KnowledgeEntity::getDeletedAt, java.time.LocalDateTime.now()));
    }

    @Override
    public void restoreKnowledgeByKb(Long workspaceId, Long kbId) {
        knowledgeMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<KnowledgeEntity>lambdaUpdate()
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getKbId, kbId)
                .eq(KnowledgeEntity::getRecycleStatus, 1)
                .set(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
                .set(KnowledgeEntity::getDeletedAt, null)
                // 恢复冲突：原目录已删除的知识挂回库根（directory_id=0）
                .set(KnowledgeEntity::getDirectoryId, 0L));
    }

    @Override
    public Map<Long, Long> countKnowledgeByKbs(Long workspaceId, List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Map.of();
        }
        List<KnowledgeEntity> rows = knowledgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .select(KnowledgeEntity::getKbId)
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
                        .in(KnowledgeEntity::getKbId, kbIds));
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (KnowledgeEntity row : rows) {
            counts.merge(row.getKbId(), 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public Map<Long, Long> countKnowledgeByDirectories(Long workspaceId, Long kbId, List<Long> directoryIds) {
        if (directoryIds == null || directoryIds.isEmpty()) {
            return Map.of();
        }
        List<KnowledgeEntity> rows = knowledgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .select(KnowledgeEntity::getDirectoryId)
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getKbId, kbId)
                        .eq(KnowledgeEntity::getRecycleStatus, RECYCLE_STATUS_NORMAL)
                        .in(KnowledgeEntity::getDirectoryId, directoryIds));
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (KnowledgeEntity row : rows) {
            counts.merge(row.getDirectoryId(), 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public void relocateKnowledgeByDirectories(Long workspaceId, Long kbId, List<Long> directoryIds, Long newDirectoryId) {
        if (directoryIds == null || directoryIds.isEmpty()) {
            return;
        }
        knowledgeMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<KnowledgeEntity>lambdaUpdate()
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getKbId, kbId)
                .in(KnowledgeEntity::getDirectoryId, directoryIds)
                .set(KnowledgeEntity::getDirectoryId, newDirectoryId));
    }

    @Override
    public ContentPageResult<RecycledKnowledgeItem> listRecycledKnowledge(Long workspaceId, long pageNo, long pageSize) {
        Page<KnowledgeEntity> page = knowledgeMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getRecycleStatus, 1)
                        .orderByDesc(KnowledgeEntity::getDeletedAt));
        List<RecycledKnowledgeItem> records = page.getRecords().stream()
                .map(k -> RecycledKnowledgeItem.builder()
                        .id(k.getId()).title(k.getTitle())
                        .kbId(k.getKbId()).directoryId(k.getDirectoryId())
                        .deletedAt(k.getDeletedAt()).build())
                .toList();
        return ContentPageResult.<RecycledKnowledgeItem>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    public RecycledKnowledgeItem getRecycledKnowledge(Long workspaceId, Long knowledgeId) {
        KnowledgeEntity knowledge = knowledgeMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getId, knowledgeId)
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getRecycleStatus, 1));
        if (knowledge == null) {
            return null;
        }
        return RecycledKnowledgeItem.builder()
                .id(knowledge.getId()).title(knowledge.getTitle())
                .kbId(knowledge.getKbId()).directoryId(knowledge.getDirectoryId())
                .deletedAt(knowledge.getDeletedAt()).build();
    }

    @Override
    public boolean restoreKnowledge(Long workspaceId, Long knowledgeId, Long directoryId) {
        return knowledgeMapper.restore(knowledgeId, workspaceId) > 0;
    }

    @Override
    public boolean purgeKnowledge(Long workspaceId, Long knowledgeId) {
        return knowledgeMapper.delete(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getId, knowledgeId)
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)) > 0;
    }

    @Override
    public void purgeKnowledgeByKb(Long workspaceId, Long kbId) {
        knowledgeMapper.delete(new LambdaQueryWrapper<KnowledgeEntity>()
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getKbId, kbId));
    }

    /** 阅读时间估算（分钟）：按 400 字/分钟，最少 1 分钟（B01/B02 卡片展示）。 */
    private int readMinutes(String content) {
        if (content == null) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(content.replaceAll("\\s+", "").length() / 400.0));
    }
}
