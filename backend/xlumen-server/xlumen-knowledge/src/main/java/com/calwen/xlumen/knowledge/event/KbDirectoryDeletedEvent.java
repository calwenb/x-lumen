package com.calwen.xlumen.knowledge.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 目录删除事件（F-0309，进程内事件）：目录（含全部子目录）删除后发布。
 * 消费方：content 模块（KB-3 content 改造）监听后将 directoryIds（被删目录及子目录）下未删除知识
 * 统一迁移到 newDirectoryId（= 被删目录的父目录，0=库根），即「删除目录时知识上挂父目录」。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbDirectoryDeletedEvent {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识库 ID。 */
    private Long kbId;

    /** 被删目录及全部子目录 ID（含自身，按父→子顺序）。 */
    private List<Long> directoryIds;

    /** 知识迁移目标目录 ID（0=库根，即被删目录的父目录）。 */
    private Long newDirectoryId;
}
