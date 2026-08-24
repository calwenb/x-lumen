package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 提交审核入参（F-0902）：知识从草稿/已通过状态提交审核。
 * publishAt 为自动审核发布模式的定时发布时间（可空，空=审核通过后立即发布）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewDTO {

    /** 知识 ID。 */
    @NotNull(message = "知识 ID 不能为空")
    private Long knowledgeId;

    /** 定时发布时间（自动模式生效，可空）。 */
    private LocalDateTime publishAt;
}
