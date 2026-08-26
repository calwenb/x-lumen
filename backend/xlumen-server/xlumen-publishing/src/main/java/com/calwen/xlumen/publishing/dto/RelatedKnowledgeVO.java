package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 相关知识推荐条目。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedKnowledgeVO {

    private Long id;

    private String title;

    private String kbName;

    private LocalDateTime publishedAt;

    /** 排序用标签交集数（同标签候选优先）。 */
    private int tagOverlap;
}