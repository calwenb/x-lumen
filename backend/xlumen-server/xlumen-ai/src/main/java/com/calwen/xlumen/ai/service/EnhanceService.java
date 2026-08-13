package com.calwen.xlumen.ai.service;

import com.calwen.xlumen.ai.dto.EnhanceRequestDTO;
import com.calwen.xlumen.ai.vo.EnhanceResultVO;

/**
 * AI 增值服务（F-0801/F-0802）：摘要/SEO 同步生成、结构化校验后落 ai_enhance_result。
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
}
