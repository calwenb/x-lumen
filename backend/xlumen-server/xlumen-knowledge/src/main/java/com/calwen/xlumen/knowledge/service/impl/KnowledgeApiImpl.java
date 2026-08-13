package com.calwen.xlumen.knowledge.service.impl;

import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import com.calwen.xlumen.knowledge.service.RetrievalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识模块对外接口实现（KnowledgeApi，M05）：三个方法直接委托索引流水线与检索服务。
 * 供 ai 模块对话检索编排（M08）与 publishing 发布事件（由本模块监听消费）调用。
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

    @Override
    public void indexArticle(IndexRequestDTO request) {
        indexPipelineService.indexArticle(request);
    }

    @Override
    public void removeArticle(Long workspaceId, Long articleId) {
        indexPipelineService.removeArticle(workspaceId, articleId);
    }

    @Override
    public List<SearchResultDTO> search(SearchRequestDTO request) {
        return retrievalService.search(request);
    }
}
