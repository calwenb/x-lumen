package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 调用追踪用量统计视图（今日）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceSummaryVO {

    private long todayCount;

    private long todayFailed;

    private List<SceneCountVO> todayByScene;

    /** 场景分布计数。 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SceneCountVO {
        private String scene;
        private long count;
    }
}