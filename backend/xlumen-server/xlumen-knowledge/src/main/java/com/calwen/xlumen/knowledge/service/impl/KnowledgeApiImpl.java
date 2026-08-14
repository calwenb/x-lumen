package com.calwen.xlumen.knowledge.service.impl;

import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.service.DirectoryService;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import com.calwen.xlumen.knowledge.service.KnowledgeBaseService;
import com.calwen.xlumen.knowledge.service.RecycleBinService;
import com.calwen.xlumen.knowledge.service.RetrievalService;
import com.calwen.xlumen.knowledge.service.VisibilityService;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识模块对外接口实现（KnowledgeApi，M05）：索引/检索委托索引流水线与检索服务；
 * KB-3（F-0308/F-0309/F-0407）知识库/目录/可见库集合推导委托对应 Service。
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

    @Override
    public void indexKnowledge(IndexRequestDTO request) {
        indexPipelineService.indexKnowledge(request);
    }

    @Override
    public void removeKnowledge(Long workspaceId, Long knowledgeId) {
        indexPipelineService.removeKnowledge(workspaceId, knowledgeId);
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
