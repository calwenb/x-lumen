package com.calwen.xlumen.content.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识编辑视图（F-0301，决策 D16）：作者本人可见的全部字段（含草稿/私有），编辑页与详情复用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVO {

    /** 知识 ID。 */
    private Long id;

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

    /** 版本号（乐观锁，提交修改时回传）。 */
    private Long version;

    /** 阅读量。 */
    private Long viewCount;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
