package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识发布/状态迁移入参（跨模块稳定类型，M10 发布与下架）：publishing 通过 ContentApi 迁移状态，
 * 版本校验失败返回 false（由调用方抛 409）。publishing 不直接操作 cnt_knowledge（BACKEND.md §5.1）。
 * KB-3 起发布目标改为 kbId+directoryId（不再传 visibility，可见性由知识库决定，决策 D16）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePublishDTO {

    /** 知识 ID。 */
    private Long knowledgeId;

    /** 期望版本号（乐观锁，不一致返回 false）。 */
    private Long expectedVersion;

    /** 目标状态（KnowledgeStatus 值）。 */
    private Integer targetStatus;

    /** 目标知识库 ID（发布时落库，决策 D16 单库单目录）。 */
    private Long kbId;

    /** 目标目录 ID（0=库根，发布时落库）。 */
    private Long directoryId;

    /** 发布时间（发布成功写入，其余迁移为 null）。 */
    private LocalDateTime publishedAt;
}
