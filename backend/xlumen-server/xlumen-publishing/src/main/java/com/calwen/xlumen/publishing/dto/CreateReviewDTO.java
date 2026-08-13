package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交审核入参（F-0902）：文章从草稿/已通过状态提交审核。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewDTO {

    /** 文章 ID。 */
    @NotNull(message = "文章 ID 不能为空")
    private Long articleId;
}
