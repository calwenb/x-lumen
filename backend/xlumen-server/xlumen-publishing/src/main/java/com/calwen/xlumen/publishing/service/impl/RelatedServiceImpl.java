package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.RelatedKnowledgeVO;
import com.calwen.xlumen.publishing.service.RelatedService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 相关知识推荐实现：同库候选按标签交集排序优先，不足 5 条用语义检索（query=标题）兜底补充。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class RelatedServiceImpl implements RelatedService {

    private static final int LIMIT = 5;

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    @Override
    public List<RelatedKnowledgeVO> related(Long knowledgeId) {
        Long userId = WorkspaceContext.userId();
        List<Long> visible = knowledgeApi.resolveVisibleKbIds(userId);
        if (visible == null || visible.isEmpty()) {
            return List.of();
        }
        KnowledgeDetailDTO current = contentApi.getPublished(null, knowledgeId, visible);
        if (current == null) {
            return List.of();
        }
        Set<String> tags = current.getTags() == null ? Set.of() : new LinkedHashSet<>(current.getTags());
        Map<Long, String> kbNames = new LinkedHashMap<>();

        // 同库候选：按标签交集数倒序，其次列表顺序（发布时间倒序已由列表保证）
        List<RelatedKnowledgeVO> candidates = new ArrayList<>();
        ContentPageResult<PublishedKnowledgeDTO> pool = contentApi.listPublished(null,
                com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO.builder()
                        .kbId(current.getKbId()).visibleKbIds(visible).pageNo(1L).pageSize(100L).build());
        for (PublishedKnowledgeDTO doc : pool.getRecords()) {
            if (doc.getId().equals(knowledgeId)) {
                continue;
            }
            int overlap = tagOverlap(tags, doc.getTags());
            RelatedKnowledgeVO vo = toVO(doc, kbNames, overlap);
            candidates.add(vo);
        }
        candidates.sort(Comparator.comparingInt(RelatedKnowledgeVO::getTagOverlap).reversed());

        // 语义兜底：不足 5 条用标题检索补足（排除自身与已有）
        Set<Long> picked = new LinkedHashSet<>();
        for (RelatedKnowledgeVO vo : candidates) {
            picked.add(vo.getId());
        }
        if (picked.size() < LIMIT && StrUtil.isNotBlank(current.getTitle())) {
            try {
                List<SearchResultDTO> hits = knowledgeApi.search(SearchRequestDTO.builder()
                        .query(current.getTitle())
                        .kbIds(visible)
                        .topK(20)
                        .build());
                for (SearchResultDTO hit : hits) {
                    if (hit.getKnowledgeId() == null || hit.getKnowledgeId().equals(knowledgeId)
                            || picked.contains(hit.getKnowledgeId())) {
                        continue;
                    }
                    picked.add(hit.getKnowledgeId());
                    candidates.add(RelatedKnowledgeVO.builder()
                            .id(hit.getKnowledgeId())
                            .title(StrUtil.blankToDefault(hit.getTitle(), ""))
                            .kbName(kbName(hit.getKnowledgeId(), kbNames))
                            .publishedAt(null)
                            .tagOverlap(0)
                            .build());
                    if (picked.size() >= LIMIT) {
                        break;
                    }
                }
            } catch (Exception ignored) {
                // 语义兜底失败仅影响数量，不阻断
            }
        }
        return candidates.stream().limit(LIMIT).toList();
    }

    private int tagOverlap(Set<String> base, List<String> docTags) {
        if (base.isEmpty() || docTags == null) {
            return 0;
        }
        int n = 0;
        for (String t : docTags) {
            if (base.contains(t)) {
                n++;
            }
        }
        return n;
    }

    private RelatedKnowledgeVO toVO(PublishedKnowledgeDTO doc, Map<Long, String> kbNames, int overlap) {
        return RelatedKnowledgeVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .kbName(kbName(doc.getKbId(), kbNames))
                .publishedAt(doc.getPublishedAt())
                .tagOverlap(overlap)
                .build();
    }

    private String kbName(Long kbId, Map<Long, String> kbNames) {
        if (kbId == null) {
            return null;
        }
        return kbNames.computeIfAbsent(kbId, id -> {
            KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBaseById(id);
            return kb == null ? null : kb.getName();
        });
    }
}