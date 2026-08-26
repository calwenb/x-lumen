package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.dto.AssistDTO;

/**
 * AI 交互式辅助服务：写作编辑四能力（续写/润色/标题/错别字）+ 代码三能力（解释/找 bug/生成测试）。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface AssistService {

    /** 按能力执行辅助，返回生成的文本（titles 为多行备选标题）。 */
    String assist(Long workspaceId, AssistDTO dto);
}