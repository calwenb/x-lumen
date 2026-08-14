package com.calwen.xlumen.content.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识列表项（F-0301，B10 列表，决策 D16）：不含正文，含库/目录/状态/版本便于筛选与编辑跳转。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeListItemVO {

    /** 知识 ID。 */
    private Long id;

    /** 标题。 */
    private String title;

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

    /** 阅读量。 */
    private Long viewCount;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
