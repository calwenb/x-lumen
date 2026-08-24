package com.calwen.xlumen.ai.service.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型发起的一次工具调用（IDEA-025 F-0708）：id 由供应商生成，tool 角色消息回引；
 * index 为流式分片归并下标（provider 内部使用，序列化请求时忽略）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 调用 ID（供应商生成）。 */
    private String id;

    /** 流式分片归并下标（provider 内部使用）。 */
    private Integer index;

    /** 工具名。 */
    private String name;

    /** 参数 JSON 字符串（流式增量拼接完整后的结果）。 */
    private String arguments;
}