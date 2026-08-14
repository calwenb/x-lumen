package com.calwen.xlumen.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索请求（跨模块稳定类型，F-0404/F-0407）：visibilityScope 由调用方按身份决定
 * （PUBLIC_ONLY 访客 / ALL 博主），knowledge 不感知角色（BACKEND.md §13）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequestDTO {

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 查询文本。 */
    private String query;

    /** 可见性范围：PUBLIC_ONLY 仅公开 / ALL 含私有。 */
    private String visibilityScope;

    /** 返回条数（≤50）。 */
    private int topK;

    /** 知识级过滤（F-0702 知识级问答：限定单篇，可空）。 */
    private Long knowledgeId;
}
