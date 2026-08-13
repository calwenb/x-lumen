package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建发布入参（F-0904）：publishAt 为空表示立即发布，非空表示定时发布（留待定时任务执行）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReleaseDTO {

    /** 文章 ID。 */
    @NotNull(message = "文章 ID 不能为空")
    private Long articleId;

    /** 文章版本号（乐观锁校验）。 */
    @NotNull(message = "版本号不能为空")
    private Long version;

    /** 可见性：1 公开 0 私有。 */
    @NotNull(message = "可见性不能为空")
    @Min(value = 0, message = "可见性取值非法")
    @Max(value = 1, message = "可见性取值非法")
    private Integer visibility;

    /** 定时发布时间（可空=立即发布）。 */
    private LocalDateTime publishAt;
}
