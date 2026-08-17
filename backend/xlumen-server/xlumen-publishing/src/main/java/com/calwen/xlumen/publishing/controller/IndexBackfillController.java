package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.knowledge.vo.IndexStatusVO;
import com.calwen.xlumen.publishing.service.IndexBackfillService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 索引补跑接口（BUG-004 存量补跑）：强制重建已发布知识的向量索引；
 * 需登录（SecurityConfig 兜底），仅当前空间已发布知识可补跑。
 * 落在 publishing 而非 knowledge 的 IndexController：正文需经 ContentApi 获取（模块依赖方向）。
 *
 * @author calwen
 * @date 2026/8/17
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class IndexBackfillController {

    @Resource
    private IndexBackfillService indexBackfillService;

    /**
     * 强制重建索引（BUG-004）：失效旧切片/版本后重跑流水线，返回最新索引状态。
     *
     * @param knowledgeId 知识 ID
     * @return 重建后的索引状态
     */
    @PostMapping("/{knowledgeId}/reindex")
    public ApiResponse<IndexStatusVO> reindex(@PathVariable Long knowledgeId) {
        return ApiResponse.success(indexBackfillService.reindex(knowledgeId));
    }
}
