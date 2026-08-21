package com.calwen.xlumen.knowledge.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.dto.RetrievalTestRequestDTO;
import com.calwen.xlumen.knowledge.service.IndexPipelineService;
import com.calwen.xlumen.knowledge.service.RetrievalService;
import com.calwen.xlumen.knowledge.vo.IndexStatusVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识索引管理接口（F-0404 检索测试 / 索引状态）：均需登录（SecurityConfig 兜底），
 * 检索测试范围为当前用户全部可见库（resolveVisibleKbIds，决策 D13），供博主校验发布即索引效果。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class IndexController {

    @Resource
    private RetrievalService retrievalService;
    @Resource
    private IndexPipelineService indexPipelineService;
    @Resource
    private KnowledgeApi knowledgeApi;

    /**
     * 检索测试（F-0404）：Embedding(query) → 向量检索，返回含 score/段落/切片的结果列表。
     *
     * @param request 检索测试入参（query/topK）
     * @return 检索结果列表
     */
    @PostMapping("/retrieval-test")
    public ApiResponse<List<SearchResultDTO>> retrievalTest(@Valid @RequestBody RetrievalTestRequestDTO request) {
        Long workspaceId = requireWorkspace();
        Long userId = WorkspaceContext.userId();
        SearchRequestDTO searchRequest = SearchRequestDTO.builder()
                .workspaceId(workspaceId)
                .query(request.getQuery())
                // 检索范围：当前用户全部可见库（公开库 + 自己私有库，决策 D13）
                .kbIds(knowledgeApi.resolveVisibleKbIds(userId))
                .topK(request.resolvedTopK())
                .build();
        return ApiResponse.success(retrievalService.search(searchRequest));
    }

    /**
     * 索引状态查询（F-0403/F-0404）：未索引返回 data=null。
     *
     * @param knowledgeId 知识 ID
     * @return 索引状态或 null
     */
    @GetMapping("/{knowledgeId}/index-status")
    public ApiResponse<IndexStatusVO> indexStatus(@PathVariable Long knowledgeId) {
        Long workspaceId = requireWorkspace();
        return ApiResponse.success(indexPipelineService.getIndexStatus(workspaceId, knowledgeId));
    }

    /** 从会话上下文取工作空间 ID，未登录抛 401。 */
    private Long requireWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }
}
