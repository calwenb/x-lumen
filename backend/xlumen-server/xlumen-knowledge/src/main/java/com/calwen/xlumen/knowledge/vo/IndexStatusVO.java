package com.calwen.xlumen.knowledge.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 索引状态视图（F-0403/F-0404）：知识当前索引版本状态，供管理面展示与重试判断。
 * 未索引时接口返回 data=null。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexStatusVO {

    /** 知识 ID。 */
    private Long knowledgeId;

    /** 已索引版本号。 */
    private Long version;

    /** 索引状态：ACTIVATING 索引中 / ACTIVE 已激活 / STALE 已失效。 */
    private String status;

    /** 切片数量（有效切片）。 */
    private Integer chunkCount;

    /** 索引完成时间。 */
    private LocalDateTime indexedAt;
}
