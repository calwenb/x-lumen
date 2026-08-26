package com.calwen.xlumen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 术语解释入参：详情页选词后向 AI 要 1~2 句解释。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermExplainDTO {

    /** 术语（选词）。 */
    @NotBlank(message = "术语不能为空")
    @Size(max = 60, message = "术语过长")
    private String term;
}