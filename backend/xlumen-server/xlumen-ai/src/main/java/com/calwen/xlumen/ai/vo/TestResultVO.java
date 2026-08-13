package com.calwen.xlumen.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型连通性测试结果（F-0502）：ok 表示连通，message 为结果说明。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResultVO {

    /** 是否连通。 */
    private boolean ok;

    /** 结果说明。 */
    private String message;
}
