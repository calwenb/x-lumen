package com.calwen.xlumen.content.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章编辑视图（F-0301）：作者本人可见的全部字段（含草稿/私有），编辑页与详情复用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVO {

    /** 文章 ID。 */
    private Long id;

    /** 标题。 */
    private String title;

    /** 正文 Markdown。 */
    private String content;

    /** 分类。 */
    private String category;

    /** 标签数组。 */
    private List<String> tags;

    /** 可见性：1 公开 0 私有（F-0307）。 */
    private Integer visibility;

    /** 状态（ArticleStatus 值）。 */
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
