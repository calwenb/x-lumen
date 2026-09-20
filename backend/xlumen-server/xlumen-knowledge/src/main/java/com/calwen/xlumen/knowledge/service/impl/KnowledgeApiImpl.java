package com.calwen.xlumen.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.KnowledgeCountApi;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.entity.KbDirectoryEntity;
import com.calwen.xlumen.knowledge.mapper.KbDirectoryMapper;
import com.calwen.xlumen.knowledge.service.DirectoryService;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import com.calwen.xlumen.knowledge.service.KnowledgeBaseService;
import com.calwen.xlumen.knowledge.service.RecycleBinService;
import com.calwen.xlumen.knowledge.service.RetrievalService;
import com.calwen.xlumen.knowledge.service.VisibilityService;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识模块对外接口实现（KnowledgeApi，M05）：索引/检索委托索引流水线与检索服务；
 * KB-3知识库/目录/可见库集合推导委托对应 Service。
 * 供 ai 模块对话检索编排（M08）与 publishing 公开读按身份聚合（resolveVisibleKbIds/getKnowledgeBase）调用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class KnowledgeApiImpl implements KnowledgeApi {

    @Resource
    private IndexPipelineService indexPipelineService;
    @Resource
    private RetrievalService retrievalService;
    @Resource
    private KnowledgeBaseService knowledgeBaseService;
    @Resource
    private DirectoryService directoryService;
    @Resource
    private VisibilityService visibilityService;
    @Resource
    private RecycleBinService recycleBinService;
    @Resource
    private KbDirectoryMapper directoryMapper;
    /** 知识数统计（反向 SPI）：实现由 content 模块提供，缺省时按 0 展示。 */
    @Resource
    private ObjectProvider<KnowledgeCountApi> knowledgeCountApiProvider;

    @Override
    public void indexKnowledge(IndexRequestDTO request) {
        indexPipelineService.indexKnowledge(request);
    }

    @Override
    public void reindexKnowledge(IndexRequestDTO request) {
        indexPipelineService.reindex(request);
    }

    @Override
    public void removeKnowledge(Long workspaceId, Long knowledgeId) {
        indexPipelineService.removeKnowledge(workspaceId, knowledgeId);
    }

    @Override
    public com.calwen.xlumen.knowledge.vo.IndexStatusVO getIndexStatus(Long workspaceId, Long knowledgeId) {
        return indexPipelineService.getIndexStatus(workspaceId, knowledgeId);
    }

    @Override
    public List<SearchResultDTO> search(SearchRequestDTO request) {
        return retrievalService.search(request);
    }

    @Override
    public List<KnowledgeBaseVO> listKnowledgeBases(Long workspaceId) {
        return knowledgeBaseService.list(workspaceId);
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBase(Long workspaceId, Long kbId) {
        return knowledgeBaseService.get(workspaceId, kbId);
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBaseById(Long kbId) {
        // workspaceId=null 跳过空间校验（多用户公开读反查库名，D9 改写）
        return knowledgeBaseService.get(null, kbId);
    }

    @Override
    public List<DirectoryVO> getDirectoryTree(Long kbId) {
        return directoryService.tree(kbId);
    }

    @Override
    public List<DirectoryVO> getPublishedDirectoryTree(Long kbId) {
        if (kbId == null) {
            return List.of();
        }
        // 跨空间只读：直接按 kbId 取目录，不经 DirectoryService（后者按会话空间校验，
        // 登录的非库主读公开库会被误判为无权而 404）；目录按名称排序由 SQL 保证
        List<KbDirectoryEntity> dirs = directoryMapper.selectList(Wrappers.<KbDirectoryEntity>lambdaQuery()
                .eq(KbDirectoryEntity::getKbId, kbId)
                .orderByAsc(KbDirectoryEntity::getName));
        if (dirs.isEmpty()) {
            return List.of();
        }
        List<Long> directoryIds = dirs.stream().map(KbDirectoryEntity::getId).toList();
        KnowledgeCountApi counter = knowledgeCountApiProvider.getIfAvailable();
        Map<Long, Long> counts = counter == null
                ? Map.of() : counter.countPublishedByDirectoryIds(kbId, directoryIds);
        // 构造新 VO 实例：不复用认证路径的 DirectoryVO 统计口径，避免公开口径污染既有语义
        Map<Long, List<DirectoryVO>> byParent = dirs.stream()
                .map(d -> toPublishedDirectoryVO(d, counts.getOrDefault(d.getId(), 0L)))
                .collect(Collectors.groupingBy(DirectoryVO::getParentId, LinkedHashMap::new, Collectors.toList()));
        byParent.values().forEach(list -> list.forEach(vo -> vo.setChildren(byParent.getOrDefault(vo.getId(), List.of()))));
        return byParent.getOrDefault(0L, List.of());
    }

    @Override
    public long countPublishedKnowledge(Long kbId) {
        if (kbId == null) {
            return 0L;
        }
        KnowledgeCountApi counter = knowledgeCountApiProvider.getIfAvailable();
        if (counter == null) {
            return 0L;
        }
        return counter.countPublishedByKbIds(List.of(kbId)).getOrDefault(kbId, 0L);
    }

    /** 公开口径目录视图：knowledgeCount 为已发布且未回收的知识数（调用方提供）。 */
    private DirectoryVO toPublishedDirectoryVO(KbDirectoryEntity d, Long knowledgeCount) {
        return DirectoryVO.builder()
                .id(d.getId())
                .kbId(d.getKbId())
                .parentId(d.getParentId())
                .name(d.getName())
                .knowledgeCount(knowledgeCount)
                .children(new ArrayList<>())
                .build();
    }

    @Override
    public boolean checkOwnership(Long workspaceId, Long kbId, Long directoryId) {
        if (workspaceId == null || kbId == null) {
            return false;
        }
        // 库存在且属于指定空间
        if (knowledgeBaseService.get(workspaceId, kbId) == null) {
            return false;
        }
        // directoryId=0 视为库根合法；否则目录必须属于该库
        if (directoryId == null || directoryId == 0L) {
            return true;
        }
        return directoryService.belongsTo(kbId, directoryId);
    }

    @Override
    public List<Long> resolveVisibleKbIds(Long userId) {
        return visibilityService.resolveVisibleKbIds(userId);
    }

    @Override
    public com.calwen.xlumen.knowledge.dto.PageResult<com.calwen.xlumen.knowledge.vo.RecycleBinItemVO> listRecycledKbs(
            Long workspaceId, long pageNo, long pageSize) {
        return recycleBinService.list("kb", com.calwen.xlumen.common.dto.PageQueryDTO.builder()
                .pageNo(pageNo).pageSize(pageSize).build());
    }

    @Override
    public void restoreRecycledKb(Long workspaceId, Long kbId) {
        recycleBinService.restore("kb", kbId);
    }

    @Override
    public void purgeRecycledKb(Long workspaceId, Long kbId) {
        recycleBinService.purge("kb", kbId, "CONFIRM");
    }
}
