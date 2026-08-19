package com.calwen.xlumen.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识版本快照（cnt_knowledge_version，F-0303 自动保存 + 历史版本）：每次落库（创建/更新/自动保存）
 * 记录当时标题与正文快照，供版本历史查询（BUG-014 补全）。
 *
 * @author calwen
 * @date 2026/8/19
 */
@Getter
@Setter
@TableName("cnt_knowledge_version")
public class KnowledgeVersionEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID（全局隔离维度）。 */
    private Long workspaceId;

    /** 知识 ID（逻辑外键 cnt_knowledge.id）。 */
    private Long knowledgeId;

    /** 版本号（对应 cnt_knowledge.version 落库后的值）。 */
    private Long version;

    /** 标题快照。 */
    private String title;

    /** 正文 Markdown 快照。 */
    private String content;

    /** 快照时间。 */
    private LocalDateTime createdAt;
}
