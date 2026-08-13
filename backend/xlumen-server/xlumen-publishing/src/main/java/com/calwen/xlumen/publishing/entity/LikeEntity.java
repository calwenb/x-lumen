package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文章点赞实体（eng_like，F-0203）：唯一键 (workspace_id, article_id, user_id) 承担幂等，点赞/取消更新 status。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Getter
@Setter
@TableName("eng_like")
public class LikeEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（逻辑外键 cnt_article.id）。 */
    private Long articleId;

    /** 点赞用户 ID（逻辑外键 iam_user.id）。 */
    private Long userId;

    /** 状态：1 已赞 0 取消。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
