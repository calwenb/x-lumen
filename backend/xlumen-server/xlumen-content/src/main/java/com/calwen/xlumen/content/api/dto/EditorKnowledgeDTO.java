package com.calwen.xlumen.content.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编辑态知识（跨模块稳定类型，BACKEND.md §5.2）：含草稿/私有全字段，
 * 供 publishing 审核读取正文（M10）与 AI 结果落库（M07 saveAiResult）使用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditorKnowledgeDTO {

    /** 知识 ID。 */
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 作者用户 ID。 */
    private Long authorId;

    /** 标题。 */
    private String title;

    /** 正文 Markdown。 */
    private String content;

    /** 所属知识库 ID（决策 D16）。 */
    private Long kbId;

    /** 所属目录 ID（0=库根）。 */
    private Long directoryId;

    /** 标签数组。 */
    private List<String> tags;

    /** 状态（KnowledgeStatus 值）。 */
    private Integer status;

    /** 版本号。 */
    private Long version;

    /** 发布时间。 */
    private LocalDateTime publishedAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
