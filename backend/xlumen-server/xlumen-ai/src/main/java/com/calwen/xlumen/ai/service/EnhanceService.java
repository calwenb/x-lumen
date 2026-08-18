package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.dto.EnhanceRequestDTO;
import com.calwen.xlumen.ai.vo.EnhanceResultVO;

/**
 * AI 增值服务（F-0801/F-0802/F-0808）：摘要/SEO 同步生成、结构化校验后落 ai_enhance_result；
 * F-0808 抽出可复用的摘要生成方法供发布事件异步监听调用。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface EnhanceService {

    /**
     * 生成增值结果：scene 仅支持 SUMMARY|SEO，结构化校验通过后落库并返回。
     *
     * @param dto 增值入参
     * @return 增值结果
     */
    EnhanceResultVO enhance(EnhanceRequestDTO dto);

    /**
     * 生成并落库知识摘要（F-0808，scene=SUMMARY）：供发布事件监听异步复用。
     * 模型调用失败/输出不合法直接抛出（BizException），由调用方决定降级策略。
     *
     * @param workspaceId 工作空间 ID（与事件 workspaceId 对齐落库）
     * @param knowledgeId 知识 ID
     * @param title       知识标题（拼入待摘要文本，可空）
     * @param content     正文 Markdown 快照
     * @return 增值结果（已落 ai_enhance_result）
     */
    EnhanceResultVO generateAndStoreSummary(Long workspaceId, Long knowledgeId, String title, String content);
}
