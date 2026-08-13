package com.calwen.xlumen.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 索引版本与活动指针实体（kb_index_version，F-0403）：同一文章多版本并存，
 * 仅一条 status=ACTIVE 为当前生效索引，旧版本置 STALE 后清理向量。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("kb_index_version")
public class KbIndexVersionEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（逻辑外键 cnt_article.id）。 */
    private Long articleId;

    /** 发布版本号（关联 cnt_article.version）。 */
    private Long version;

    /** 向量索引名（Milvus 集合名）。 */
    private String indexName;

    /** Embedding 模型名。 */
    private String embeddingModel;

    /** 状态：ACTIVATING 索引中 / ACTIVE 已激活 / STALE 已失效。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
