package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.dto.ReviewRequestDTO;

/**
 * AI 审校服务（F-0604）：content 必填、模型异源校验、创建 REVIEWER 异步任务。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ReviewService {

    /**
     * 提交审校任务：写作与审校模型必须异源，幂等键=workspaceId+内容 hash。
     *
     * @param dto 审校入参
     * @return 任务 ID
     */
    Long submit(ReviewRequestDTO dto);
}
