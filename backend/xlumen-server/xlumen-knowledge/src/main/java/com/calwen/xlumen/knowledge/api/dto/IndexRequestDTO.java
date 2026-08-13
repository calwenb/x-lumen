package com.calwen.xlumen.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引请求（跨模块稳定类型）：发布即索引（F-0402）流水线入参，正文快照由发布事件携带。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRequestDTO {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID。 */
    private Long articleId;

    /** 发布版本号。 */
    private Long version;

    /** 标题。 */
    private String title;

    /** 正文 Markdown 快照。 */
    private String content;

    /** 可见性：1 公开 0 私有（F-0407 检索过滤字段）。 */
    private Integer visibility;
}
