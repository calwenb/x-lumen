package com.calwen.xlumen.knowledge.api;

import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;

import java.util.List;

/**
 * 知识模块对外接口（BACKEND.md §5.2）：发布即索引（F-0402）与 RAG 检索（F-0404/F-0407），
 * 供 ai 模块对话检索编排（M08）与 publishing 发布事件触发（M10 事件由本模块监听消费）。
 * 实现：service/impl/KnowledgeApiImpl（M05）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface KnowledgeApi {

    /**
     * 索引文章（F-0402）：切片→Embedding→写新索引→检索校验→激活→清理旧版本。
     * 异步流水线失败不影响发布（索引状态可查询重试）。
     *
     * @param request 索引请求（含正文快照）
     */
    void indexArticle(IndexRequestDTO request);

    /**
     * 移除文章索引（删除/下架同步出索引，F-0402）。
     *
     * @param workspaceId 工作空间 ID
     * @param articleId   文章 ID
     */
    void removeArticle(Long workspaceId, Long articleId);

    /**
     * 向量检索（F-0407 按身份过滤由 visibilityScope 入参决定）。
     *
     * @param request 检索请求
     * @return 检索结果（按分数降序）
     */
    List<SearchResultDTO> search(SearchRequestDTO request);
}
