package com.calwen.xlumen.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 切片元数据实体（kb_chunk，F-0402/F-0405）：正文按标题边界切片落库，
 * vector_id 指向向量库条目（Noop 降级时留空），content_hash 用于发布幂等。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("kb_chunk")
public class KbChunkEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 知识 ID（逻辑外键 cnt_knowledge.id）。 */
    private Long knowledgeId;

    /** 发布版本号（关联 kb_index_version.version）。 */
    private Long version;

    /** 切片序号（从 1 开始，引用溯源定位）。 */
    private Integer chunkSeq;

    /** 段落标题锚点（Markdown 标题，跳转原文定位）。 */
    private String headingAnchor;

    /** 正文 SHA-256 哈希（幂等检查）。 */
    private String contentHash;

    /** 向量库条目 ID（Noop 降级时留空）。 */
    private String vectorId;

    /** 切片文本。 */
    private String chunkText;

    /** 状态：1 有效 0 失效。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
