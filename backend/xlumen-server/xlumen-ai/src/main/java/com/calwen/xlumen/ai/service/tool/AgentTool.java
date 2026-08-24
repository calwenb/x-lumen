package com.calwen.xlumen.ai.service.tool;

import cn.hutool.json.JSONObject;
import com.calwen.xlumen.ai.enums.AiScene;

import java.util.Set;

/**
 * Agent 工具契约（IDEA-025 F-0708）：工具实现声明名字/描述/参数 Schema/适用场景，
 * execute 返回给模型阅读的结果文本（结构化信封 JSON），内部异常必须捕获并返回错误信封，不得抛出。
 * 本批工具全部只读、全部包装 KnowledgeApi 现有方法（红线：AI 不反向依赖 Content）。
 *
 * @author calwen
 * @date 2026/8/24
 */
public interface AgentTool {

    /** 工具名（全仓唯一，点分命名如 knowledge.search）。 */
    String name();

    /** 给模型看的功能描述。 */
    String description();

    /** 参数 JSON Schema（文本）。 */
    String parametersSchema();

    /** 声明适用的场景（本批挂 QA/REVIEWER；WRITING 留待 F-0603）。 */
    Set<AiScene> scenes();

    /** 执行并返回结果信封文本；异常由实现自行兜底为错误信封。 */
    String execute(ToolContext ctx, JSONObject args);
}