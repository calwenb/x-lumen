package com.calwen.xlumen.publishing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建发布入参（F-0904，决策 D16）：publishAt 为空表示立即发布，非空表示定时发布（留待定时任务执行）；
 * 发布目标（库/目录）由知识本身归属决定（KB-3 起删除文章级可见性入参，可见性由知识库决定）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReleaseDTO {

    /** 知识 ID。 */
    @NotNull(message = "知识 ID 不能为空")
    private Long knowledgeId;

    /** 知识版本号（乐观锁校验）。 */
    @NotNull(message = "版本号不能为空")
    private Long version;

    /** 定时发布时间（可空=立即发布）。 */
    private LocalDateTime publishAt;
}
