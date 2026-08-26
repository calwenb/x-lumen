package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站点更新日志实体（plt_changelog）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Getter
@Setter
@TableName("plt_changelog")
public class ChangelogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;

    private String title;

    private String content;

    private Integer published;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}