package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文章发布记录实体（pub_release，F-0904/F-0905）：唯一键 uk_release_ws_article_version 幂等；
 * publish_at 为空表示立即发布，非空留待定时任务执行（幂等）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("pub_release")
public class ReleaseEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（逻辑外键 cnt_article.id）。 */
    private Long articleId;

    /** 文章标题（冗余展示字段）。 */
    private String articleTitle;

    /** 文章版本号（发布时快照）。 */
    private Long version;

    /** 可见性：1 公开 0 私有。 */
    private Integer visibility;

    /** 定时发布时间（NULL=立即发布）。 */
    private LocalDateTime publishAt;

    /** 实际发布时间。 */
    private LocalDateTime releasedAt;

    /** 状态：PENDING 待发布/DONE 已发布/FAILED 失败。 */
    private String status;

    /** 幂等键。 */
    private String idempotencyKey;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
