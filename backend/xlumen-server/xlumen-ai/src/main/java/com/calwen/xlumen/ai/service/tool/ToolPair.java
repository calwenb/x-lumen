package com.calwen.xlumen.ai.service.tool;

import com.calwen.xlumen.ai.enums.AiScene;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用配对（OPT-2/D20）：assistant.tool_calls 行与 tool 行一一对应的落库数据。
 * toolCallId 为适配层合成的本地关联 ID（tc-序号），仅保证 chat_message 表内一致性，
 * 供历史回放配对修剪与前端工具面板归并使用——语义与迁移前 provider tool_call_id 一致。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPair {

    /** 本地合成工具调用 ID（tc-序号）。 */
    private String toolCallId;

    /** 工具名。 */
    private String name;

    /** 模型传入的参数 JSON 文本。 */
    private String arguments;

    /** 工具返回信封（ok/error）。 */
    private String content;

    /** 是否成功。 */
    private boolean ok;

    /** 本地引用的场景枚举（预算计算用，冗余于此避免 sink 自持）。 */
    private AiScene scene;
}