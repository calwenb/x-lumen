package com.calwen.xlumen.knowledge.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索请求（跨模块稳定类型，F-0404/F-0407）：可见库集合 kbIds 由调用方按身份推导
 * （resolveVisibleKbIds：访客=公开库 / 登录=+自己私有库，决策 D13），knowledge 不感知角色
 * （BACKEND.md §13）。kbIds 为空=无可见库，检索返回空列表。
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

    /** 可见库集合过滤（F-0407 决策 D13，替代 visibilityScope；为空=无可见库返回空）。 */
    private List<Long> kbIds;

    /** 返回条数（≤50）。 */
    private int topK;

    /** 知识级过滤（F-0702 知识级问答：限定单篇，可空）。 */
    private Long knowledgeId;
}
