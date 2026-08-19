package com.calwen.xlumen.content.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识版本视图（F-0303 历史版本）：版本号 + 标题/正文快照 + 快照时间。
 *
 * @author calwen
 * @date 2026/8/19
 */
@Data
@Builder
public class KnowledgeVersionVO {

    /** 版本号。 */
    private Long version;

    /** 标题快照。 */
    private String title;

    /** 正文 Markdown 快照。 */
    private String content;

    /** 快照时间。 */
    private LocalDateTime createdAt;
}
