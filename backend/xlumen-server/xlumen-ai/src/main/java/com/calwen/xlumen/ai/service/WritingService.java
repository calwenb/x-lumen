package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.dto.WritingRequestDTO;

/**
 * AI 写作服务（F-0601）：校验入参、生成幂等键、创建 WRITING 异步任务。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface WritingService {

    /**
     * 提交写作任务：topic/draft/content 至少填写一项；幂等键=workspaceId+内容 hash。
     *
     * @param dto 写作入参
     * @return 任务 ID
     */
    Long submit(WritingRequestDTO dto);
}
