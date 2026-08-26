package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 全量重建索引汇总视图。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReindexAllVO {

    /** 空间已发布知识总数（可重建对象）。 */
    private long total;

    /** 成功数。 */
    private long ok;

    /** 失败明细（知识 ID + 原因）。 */
    private List<FailedItem> failed;

    /** 单条失败明细。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private Long knowledgeId;
        private String reason;
    }
}