package com.calwen.xlumen.publishing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识审核记录实体（pub_review）：AI 审校结果快照 ai_result_json，
 * knowledge_title 冗余展示字段（列表免 N+1）；状态 PENDING/APPROVED/REJECTED。
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

    /** 知识 ID（逻辑外键 cnt_knowledge.id）。 */
    private Long knowledgeId;

    /** 知识标题（冗余展示字段）。 */
    private String knowledgeTitle;

    /** 知识版本号（提交审核时快照）。 */
    private Long version;

    /** 审核人用户 ID（逻辑外键 iam_user.id）。 */
    private Long reviewerId;

    /** AI 审校任务 ID（逻辑外键 ai_task.id，可空）。 */
    private Long aiTaskId;

    /** AI 任务状态镜像（事件驱动，COMPLETED/FAILED 等，展示用，可空）。 */
    private String aiStatus;

    /** AI 任务失败原因镜像（事件驱动，可空）。 */
    private String aiError;

    /** AI 审校结果快照（JSON 文本，可空）。 */
    private String aiResultJson;

    /** 状态：PENDING 待审核/APPROVED 通过/REJECTED 驳回。 */
    private String status;

    /** 自动审核发布模式（1=发布按钮提交，审核通过后自动发布；0=审核中心人工提交）。 */
    private Integer autoMode;

    /** 定时发布时间（自动模式生效，NULL=立即发布）。 */
    private LocalDateTime autoPublishAt;

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
