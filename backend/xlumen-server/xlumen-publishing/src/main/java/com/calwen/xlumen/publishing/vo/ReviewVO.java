package com.calwen.xlumen.publishing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核记录视图（F-0902/F-0903）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {

    /** 审核记录 ID。 */
    private Long id;

    /** 知识 ID。 */
    private Long knowledgeId;

    /** 知识标题。 */
    private String knowledgeTitle;

    /** 知识版本号。 */
    private Long version;

    /** 状态：PENDING/APPROVED/REJECTED。 */
    private String status;

    /** AI 审校任务 ID（可空）。 */
    private Long aiTaskId;

    /** AI 审校结果快照（可空）。 */
    private String aiResultJson;

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
