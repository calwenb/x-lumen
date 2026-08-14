package com.calwen.xlumen.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 目录树实体（kb_directory，F-0309，决策 D16）：parent_id 多级自关联（0=库根），
 * 列表按名称排序（数据库排序规则，不设拼音列）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@Getter
@Setter
@TableName("kb_directory")
public class KbDirectoryEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 ID（逻辑外键 kb_knowledge_base.id）。 */
    private Long kbId;

    /** 父目录 ID（0=根目录）。 */
    private Long parentId;

    /** 目录名称。 */
    private String name;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
