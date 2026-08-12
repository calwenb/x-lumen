package com.calwen.xlumen.content.editor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章实体（cnt_article，F-0201）：公开读字段 M03 落地，编辑相关字段随 M04 扩展。
 * tags 为 MySQL JSON 列，经 JacksonTypeHandler 映射 List&lt;String&gt;。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName(value = "cnt_article", autoResultMap = true)
public class ArticleEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID（全局隔离维度）。 */
    private Long workspaceId;

    /** 作者用户 ID（逻辑外键 iam_user.id）。 */
    private Long authorId;

    /** 作者名（冗余展示字段）。 */
    private String authorName;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String summary;

    /** 正文 Markdown（已发布版本正文快照）。 */
    private String content;

    /** 分类（公开筛选维度，F-0202）。 */
    private String category;

    /** 标签数组（公开筛选维度，F-0202）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** 状态：1 草稿 2 已发布 3 已下架（8 状态机随 M10 细化）。 */
    private Integer status;

    /** 可见性：1 公开 0 私有（F-0307，私有不进公开列表与搜索）。 */
    private Integer visibility;

    /** 阅读量（F-0203，Redis 防刷后自增）。 */
    private Long viewCount;

    /** 发布时间（已发布后非空）。 */
    private LocalDateTime publishedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
