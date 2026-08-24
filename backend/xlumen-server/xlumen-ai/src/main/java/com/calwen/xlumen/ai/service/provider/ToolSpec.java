package com.calwen.xlumen.ai.service.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具定义（OpenAI tools 协议的 function 描述，IDEA-025 F-0708）：name 全仓唯一（点分命名如 knowledge.search）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolSpec {

    /** 工具名（全仓唯一，点分命名 knowledge.search / knowledge.list / knowledge.getDirectoryTree）。 */
    private String name;

    /** 给模型看的功能描述。 */
    private String description;

    /** 参数 JSON Schema（文本，请求体序列化时解析为对象）。 */
    private String parameters;
}