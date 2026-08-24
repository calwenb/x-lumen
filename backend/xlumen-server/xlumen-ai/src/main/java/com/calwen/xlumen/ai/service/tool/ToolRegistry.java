package com.calwen.xlumen.ai.service.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.provider.ToolCall;
import com.calwen.xlumen.ai.service.provider.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表（IDEA-025 F-0708）：构造时收集全部 AgentTool Bean；specsFor(scene) 供编排层组装 tools 参数；
 * execute() 负责白名单校验（模型捏造的未知工具名 → 错误信封）、超时（独立 future，孤儿结果丢弃）、
 * 结果截断、异常兜底——错误一律以信封回给模型（模型可换库/改关键词或声明无法获取后作答），不上抛。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** 工具执行线程池（超时防护用；任务短，固定小池）。 */
    private static final int TOOL_POOL_SIZE = 4;

    private final Map<String, AgentTool> tools;
    private final AiProperties aiProperties;
    private final ExecutorService toolExecutor;

    public ToolRegistry(List<AgentTool> toolList, AiProperties aiProperties) {
        this.tools = toolList.stream()
                .collect(Collectors.toMap(AgentTool::name, Function.identity(), (a, b) -> a));
        this.aiProperties = aiProperties;
        this.toolExecutor = Executors.newFixedThreadPool(TOOL_POOL_SIZE, r -> {
            Thread t = new Thread(r, "xlumen-agent-tool");
            t.setDaemon(true);
            return t;
        });
    }

    /** 某场景可挂的工具定义列表。 */
    public List<ToolSpec> specsFor(AiScene scene) {
        List<ToolSpec> specs = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            if (tool.scenes().contains(scene)) {
                specs.add(ToolSpec.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .parameters(tool.parametersSchema())
                        .build());
            }
        }
        return specs;
    }

    /**
     * 执行一次工具调用：白名单校验 + 超时 + 结果截断 + 异常兜底，一律返回信封，不向编排层抛出。
     */
    public String execute(ToolContext ctx, ToolCall call) {
        AgentTool tool = tools.get(call.getName());
        if (tool == null) {
            return errorEnvelope("未知工具：" + call.getName());
        }
        JSONObject args = parseArgs(call.getArguments());
        Future<String> future = toolExecutor.submit(() -> tool.execute(ctx, args));
        try {
            String result = future.get(aiProperties.getAgentToolTimeoutMillis(), TimeUnit.MILLISECONDS);
            return truncateResult(result, aiProperties.getAgentToolResultMaxChars());
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("工具执行超时 name={}", call.getName());
            return errorEnvelope("工具执行超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return errorEnvelope("工具执行被中断");
        } catch (Exception e) {
            log.warn("工具执行异常 name={}", call.getName(), e);
            return errorEnvelope("工具执行异常");
        }
    }

    /** 成功信封。 */
    public static String okEnvelope(Object data) {
        return JSONUtil.createObj().set("ok", true).set("data", data).toString();
    }

    /** 错误信封。 */
    public static String errorEnvelope(String error) {
        return JSONUtil.createObj().set("ok", false).set("error", error).toString();
    }

    /** 超长结果截断并附 truncated:true。 */
    private String truncateResult(String result, int maxChars) {
        if (result == null) {
            return errorEnvelope("工具返回空结果");
        }
        if (result.length() <= maxChars) {
            return result;
        }
        String cut = result.substring(0, maxChars);
        return JSONUtil.createObj().set("ok", true).set("data", cut).set("truncated", true).toString();
    }

    /** 参数解析：非法 JSON 按空对象处理（工具自行校验必填字段）。 */
    private JSONObject parseArgs(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return JSONUtil.createObj();
        }
        try {
            return JSONUtil.parseObj(arguments);
        } catch (Exception e) {
            return JSONUtil.createObj();
        }
    }
}