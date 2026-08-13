package com.calwen.xlumen.knowledge.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
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
 * 检索测试范围含私有（ALL），供博主校验发布即索引效果。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class IndexController {

    /** 检索测试可见性范围：博主含私有。 */
    private static final String VISIBILITY_ALL = "ALL";

    @Resource
    private RetrievalService retrievalService;
    @Resource
    private IndexPipelineService indexPipelineService;

    /**
     * 检索测试（F-0404）：Embedding(query) → 向量检索，返回含 score/段落/切片的结果列表。
     *
     * @param request 检索测试入参（query/topK）
     * @return 检索结果列表
     */
    @PostMapping("/retrieval-test")
    public ApiResponse<List<SearchResultDTO>> retrievalTest(@Valid @RequestBody RetrievalTestRequestDTO request) {
        Long workspaceId = requireWorkspace();
        SearchRequestDTO searchRequest = SearchRequestDTO.builder()
                .workspaceId(workspaceId)
                .query(request.getQuery())
                .visibilityScope(VISIBILITY_ALL)
                .topK(request.getTopK())
                .build();
        return ApiResponse.success(retrievalService.search(searchRequest));
    }

    /**
     * 索引状态查询（F-0403/F-0404）：未索引返回 data=null。
     *
     * @param articleId 文章 ID
     * @return 索引状态或 null
     */
    @GetMapping("/articles/{articleId}/index-status")
    public ApiResponse<IndexStatusVO> indexStatus(@PathVariable Long articleId) {
        Long workspaceId = requireWorkspace();
        return ApiResponse.success(indexPipelineService.getIndexStatus(workspaceId, articleId));
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
