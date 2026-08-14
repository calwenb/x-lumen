package com.calwen.xlumen.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引请求（跨模块稳定类型）：发布即索引（F-0402）流水线入参，正文快照由发布事件携带。
 * KB-3 起携带 kbId（决策 D13 索引按库切分，切片/版本元数据落库）。
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

    /** 所属知识库 ID（决策 D13 索引按库切分，KB-3 起携带）。 */
    private Long kbId;

    /** 知识 ID。 */
    private Long knowledgeId;

    /** 发布版本号。 */
    private Long version;

    /** 标题。 */
    private String title;

    /** 正文 Markdown 快照。 */
    private String content;
}
