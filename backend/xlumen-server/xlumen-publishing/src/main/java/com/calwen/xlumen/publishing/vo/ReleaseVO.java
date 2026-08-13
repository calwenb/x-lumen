package com.calwen.xlumen.publishing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发布记录视图（F-0904/F-0905）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseVO {

    /** 发布记录 ID。 */
    private Long id;

    /** 文章 ID。 */
    private Long articleId;

    /** 文章标题。 */
    private String articleTitle;

    /** 文章版本号。 */
    private Long version;

    /** 可见性：1 公开 0 私有。 */
    private Integer visibility;

    /** 定时发布时间（可空=立即发布）。 */
    private LocalDateTime publishAt;

    /** 实际发布时间。 */
    private LocalDateTime releasedAt;

    /** 状态：PENDING/DONE/FAILED。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
