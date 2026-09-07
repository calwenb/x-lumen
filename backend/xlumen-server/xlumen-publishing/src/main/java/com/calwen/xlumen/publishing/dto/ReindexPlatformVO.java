package com.calwen.xlumen.publishing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全平台索引补跑视图：异步任务触发回执 + 进度查询共用。
 * running=false 且 total=0 表示从未触发过。
 *
 * @author calwen
 * @date 2026/9/7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReindexPlatformVO {

    /** 本次触发是否真正启动了任务（false=已有任务在跑或服务端刚重启，返回的是当前进度）。 */
    private boolean started;

    /** 任务是否仍在运行。 */
    private boolean running;

    /** 触发时刻的全平台已发布知识总数。 */
    private long total;

    /** 已处理条数（含失败）。 */
    private long processed;

    /** 成功条数。 */
    private long ok;

    /** 失败条数。 */
    private long failedCount;

    /** 失败明细（最多保留前 100 条）。 */
    private List<ReindexAllVO.FailedItem> failed;

    /** 任务开始时间。 */
    private LocalDateTime startedAt;

    /** 任务结束时间（null=仍在运行）。 */
    private LocalDateTime finishedAt;
}
