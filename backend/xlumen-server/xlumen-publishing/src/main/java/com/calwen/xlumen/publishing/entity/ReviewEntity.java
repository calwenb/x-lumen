package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文章审核记录实体（pub_review，F-0902/F-0903）：AI 审校结果快照 ai_result_json，
 * article_title 冗余展示字段（列表免 N+1）；状态 PENDING/APPROVED/REJECTED。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Getter
@Setter
@TableName("pub_review")
public class ReviewEntity {

    /** 主键（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作空间 ID。 */
    private Long workspaceId;

    /** 文章 ID（逻辑外键 cnt_article.id）。 */
    private Long articleId;

    /** 文章标题（冗余展示字段）。 */
    private String articleTitle;

    /** 文章版本号（提交审核时快照）。 */
    private Long version;

    /** 审核人用户 ID（逻辑外键 iam_user.id）。 */
    private Long reviewerId;

    /** AI 审校任务 ID（逻辑外键 ai_task.id，可空）。 */
    private Long aiTaskId;

    /** AI 审校结果快照（JSON 文本，可空）。 */
    private String aiResultJson;

    /** 状态：PENDING 待审核/APPROVED 通过/REJECTED 驳回。 */
    private String status;

    /** 驳回原因。 */
    private String rejectReason;

    /** 驳回位置。 */
    private String rejectPosition;

    /** 驳回期望。 */
    private String rejectExpectation;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
