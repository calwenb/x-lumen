package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.config.ScriptedChatModel;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiCallLogService;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.QuotaService;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.tool.AgentTool;
import com.calwen.xlumen.ai.service.tool.ToolCallbackAdapter;
import com.calwen.xlumen.ai.service.tool.ToolRun;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 运行时实现（D20 全量迁移）：场景解析（表优先回退）、供应商 ChatModel 懒装配缓存、
 * 简单熔断（连续失败 5 次熔断 60s）、无密钥回退 ScriptedChatModel；对话全走 Spring AI
 * ChatModel/ChatClient。工具路径把业务 AgentTool 注册为 ToolCallback 交给 ChatClient 自动循环，
 * 并注入业务 AgentToolContext 与 ToolEventSink（KEY_* 常量见 ToolCallbackAdapter）。
 * V2 基建：所有 LLM 调用经配额预占（超限 429）与调用追踪埋点（ai_call_log）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Service
public class ChatRuntimeImpl implements ChatRuntime {

    private static final Logger log = LoggerFactory.getLogger(ChatRuntimeImpl.class);

    /** 连续失败熔断阈值。 */
    private static final int FAILURE_THRESHOLD = 5;
    /** 熔断打开时长（毫秒）。 */
    private static final long OPEN_MILLIS = 60_000L;
    /** 对外统一熔断提示。 */
    private static final String CIRCUIT_MESSAGE = "AI 服务暂时不可用，请稍后重试";
    /** 供应商客户端请求超时。 */
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(60);
    /** 供应商客户端重试次数（收紧：审校发布闸门最坏耗时不放大）。 */
    private static final int MAX_RETRIES = 2;
    /** 工具执行线程池（超时防护用；任务短，固定小池）。 */
    private static final int TOOL_POOL_SIZE = 4;

    private final SceneConfigService sceneConfigService;
    private final AiProperties aiProperties;
    private final ScriptedChatModel scriptedChatModel;
    private final QuotaService quotaService;
    private final AiCallLogService aiCallLogService;
    private final List<AgentTool> agentTools;
    private final ExecutorService toolExecutor;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public ChatRuntimeImpl(SceneConfigService sceneConfigService,
                           AiProperties aiProperties,
                           ScriptedChatModel scriptedChatModel,
                           QuotaService quotaService,
                           AiCallLogService aiCallLogService,
                           List<AgentTool> agentTools) {
        this.sceneConfigService = sceneConfigService;
        this.aiProperties = aiProperties;
        this.scriptedChatModel = scriptedChatModel;
        this.quotaService = quotaService;
        this.aiCallLogService = aiCallLogService;
        this.agentTools = agentTools;
        this.toolExecutor = Executors.newFixedThreadPool(TOOL_POOL_SIZE, r -> {
            Thread t = new Thread(r, "xlumen-agent-tool");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public SceneModel resolveScene(Long workspaceId, AiScene scene) {
        return sceneConfigService.resolve(workspaceId, scene);
    }

    @Override
    public String chat(Long workspaceId, AiScene scene, List<Message> messages,
                       Double temperature, Integer maxTokens) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ChatModel model = resolveModel(sm);
        String key = circuitKey(sm);
        checkCircuit(key);
        quotaService.reserve(workspaceId, scene);
        boolean degraded = model == scriptedChatModel;
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMsg = "";
        int tokensIn = 0;
        int tokensOut = 0;
        try {
            ChatResponse response = model.call(new Prompt(messages, openAiOptions(sm, temperature, maxTokens)));
            recordSuccess(key);
            success = true;
            int[] usage = usageOf(response);
            tokensIn = usage[0];
            tokensOut = usage[1];
            return textOf(response);
        } catch (Exception e) {
            quotaService.release(workspaceId, scene);
            log.warn("AI 对话调用失败 provider={} model={}", sm.getProviderName(), sm.getModel(), e);
            recordFailure(key);
            errorMsg = safeMessage(e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, CIRCUIT_MESSAGE);
        } finally {
            aiCallLogService.record(workspaceId, WorkspaceContext.userId(), scene, null, "CHAT",
                    sm.getProviderName(), sm.getModel(), promptHash(messages),
                    tokensIn, tokensOut, System.currentTimeMillis() - start, success, degraded, errorMsg);
        }
    }

    @Override
    public String chatWithModel(Long workspaceId, AiScene scene, String modelName, List<Message> messages,
                                Double temperature, Integer maxTokens) {
        ChatModel model = providerModel("BAILIAN", aiProperties.getBailianBaseUrl(), aiProperties.getBailianApiKey());
        String provider = "BAILIAN";
        String key = provider + ":" + modelName;
        checkCircuit(key);
        quotaService.reserve(workspaceId, scene);
        boolean degraded = model == scriptedChatModel;
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMsg = "";
        int tokensIn = 0;
        int tokensOut = 0;
        try {
            ChatResponse response = model.call(new Prompt(messages, openAiOptions(modelName, temperature, maxTokens)));
            recordSuccess(key);
            success = true;
            int[] usage = usageOf(response);
            tokensIn = usage[0];
            tokensOut = usage[1];
            return textOf(response);
        } catch (Exception e) {
            quotaService.release(workspaceId, scene);
            log.warn("AI 专用模型调用失败 provider={} model={}", provider, modelName, e);
            recordFailure(key);
            errorMsg = safeMessage(e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, CIRCUIT_MESSAGE);
        } finally {
            aiCallLogService.record(workspaceId, WorkspaceContext.userId(), scene, null, "CHAT",
                    provider, modelName, promptHash(messages),
                    tokensIn, tokensOut, System.currentTimeMillis() - start, success, degraded, errorMsg);
        }
    }

    @Override
    public void chatStream(Long workspaceId, AiScene scene, List<Message> messages,
                           Double temperature, Integer maxTokens,
                           Consumer<String> onChunk, Consumer<Throwable> onError) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ChatModel model = resolveModel(sm);
        String key = circuitKey(sm);
        try {
            checkCircuit(key);
        } catch (BizException e) {
            onError.accept(e);
            return;
        }
        quotaService.reserve(workspaceId, scene);
        boolean degraded = model == scriptedChatModel;
        long start = System.currentTimeMillis();
        AtomicBoolean errored = new AtomicBoolean(false);
        int[] usage = {0, 0};
        try {
            model.stream(new Prompt(messages, openAiOptions(sm, temperature, maxTokens)))
                    .doOnNext(r -> {
                        String text = textOf(r);
                        if (StrUtil.isNotBlank(text)) {
                            onChunk.accept(text);
                        }
                        int[] u = usageOf(r);
                        if (u[0] > usage[0]) {
                            usage[0] = u[0];
                        }
                        if (u[1] > usage[1]) {
                            usage[1] = u[1];
                        }
                    })
                    .doOnError(e -> {
                        errored.set(true);
                        recordFailure(key);
                        onError.accept(e);
                    })
                    .blockLast();
            if (!errored.get()) {
                recordSuccess(key);
            }
        } catch (Exception e) {
            if (!errored.get()) {
                log.warn("AI 流式调用失败 provider={} model={}", sm.getProviderName(), sm.getModel(), e);
                recordFailure(key);
                onError.accept(e);
            }
        } finally {
            if (errored.get()) {
                quotaService.release(workspaceId, scene);
            }
            aiCallLogService.record(workspaceId, WorkspaceContext.userId(), scene, null, "CHAT_STREAM",
                    sm.getProviderName(), sm.getModel(), promptHash(messages),
                    usage[0], usage[1], System.currentTimeMillis() - start, !errored.get(), degraded, "");
        }
    }

    @Override
    public String chatWithTools(Long workspaceId, AiScene scene, List<Message> messages,
                                Double temperature, Integer maxTokens, ToolRun run) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ChatModel model = resolveModel(sm);
        String key = circuitKey(sm);
        checkCircuit(key);
        quotaService.reserve(workspaceId, scene);
        boolean degraded = model == scriptedChatModel;
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMsg = "";
        int tokensIn = 0;
        int tokensOut = 0;
        try {
            ChatClient client = ChatClient.builder(model).build();
            List<ToolCallback> callbacks = toolsFor(run);
            ChatResponse response = client.prompt()
                    .messages(messages)
                    .options(OpenAiChatOptions.builder()
                            .model(sm.getModel())
                            .temperature(temperature)
                            .maxTokens(maxTokens)
                            .toolCallbacks(callbacks.toArray(ToolCallback[]::new))
                            .toolContext(toolContextMap(run)))
                    .call()
                    .chatResponse();
            recordSuccess(key);
            success = true;
            int[] usage = usageOf(response);
            tokensIn = usage[0];
            tokensOut = usage[1];
            return response == null ? "" : textOf(response);
        } catch (Exception e) {
            quotaService.release(workspaceId, scene);
            log.warn("AI 工具化调用失败 provider={} model={}", sm.getProviderName(), sm.getModel(), e);
            recordFailure(key);
            errorMsg = safeMessage(e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, CIRCUIT_MESSAGE);
        } finally {
            aiCallLogService.record(workspaceId, WorkspaceContext.userId(), scene, null, "CHAT_TOOLS",
                    sm.getProviderName(), sm.getModel(), promptHash(messages),
                    tokensIn, tokensOut, System.currentTimeMillis() - start, success, degraded, errorMsg);
        }
    }

    @Override
    public void chatStreamWithTools(Long workspaceId, AiScene scene, List<Message> messages,
                                    Double temperature, Integer maxTokens, ToolRun run,
                                    Consumer<String> onChunk, Consumer<Throwable> onError) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ChatModel model = resolveModel(sm);
        String key = circuitKey(sm);
        try {
            checkCircuit(key);
        } catch (BizException e) {
            onError.accept(e);
            return;
        }
        quotaService.reserve(workspaceId, scene);
        boolean degraded = model == scriptedChatModel;
        long start = System.currentTimeMillis();
        ChatClient client = ChatClient.builder(model).build();
        AtomicBoolean errored = new AtomicBoolean(false);
        int[] usage = {0, 0};
        try {
            List<ToolCallback> callbacks = toolsFor(run);
            client.prompt()
                    .messages(messages)
                    .options(OpenAiChatOptions.builder()
                            .model(sm.getModel())
                            .temperature(temperature)
                            .maxTokens(maxTokens)
                            .toolCallbacks(callbacks.toArray(ToolCallback[]::new))
                            .toolContext(toolContextMap(run)))
                    .stream()
                    .chatResponse()
                    .doOnNext(r -> {
                        String text = textOf(r);
                        if (StrUtil.isNotBlank(text)) {
                            onChunk.accept(text);
                        }
                        int[] u = usageOf(r);
                        if (u[0] > usage[0]) {
                            usage[0] = u[0];
                        }
                        if (u[1] > usage[1]) {
                            usage[1] = u[1];
                        }
                    })
                    .doOnError(e -> {
                        errored.set(true);
                        recordFailure(key);
                        onError.accept(e);
                    })
                    .blockLast();
            if (!errored.get()) {
                recordSuccess(key);
            }
        } catch (Exception e) {
            if (!errored.get()) {
                log.warn("AI 工具化流式调用失败 provider={} model={}", sm.getProviderName(), sm.getModel(), e);
                recordFailure(key);
                onError.accept(e);
            }
        } finally {
            if (errored.get()) {
                quotaService.release(workspaceId, scene);
            }
            aiCallLogService.record(workspaceId, WorkspaceContext.userId(), scene, null, "CHAT_STREAM_TOOLS",
                    sm.getProviderName(), sm.getModel(), promptHash(messages),
                    usage[0], usage[1], System.currentTimeMillis() - start, !errored.get(), degraded, "");
        }
    }

    @Override
    public boolean test(String providerName, String model) {
        ChatModel chatModel = resolveModelStrict(providerName);
        Prompt prompt = new Prompt(List.of(new UserMessage("ping")),
                OpenAiChatOptions.builder().model(model).temperature(0.0).maxTokens(16).build());
        try {
            ChatResponse resp = chatModel.call(prompt);
            return StrUtil.isNotBlank(textOf(resp));
        } catch (Exception e) {
            log.warn("模型连通性测试失败 provider={} model={}", providerName, model, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "连接失败：" + safeMessage(e));
        }
    }

    /** 场景工具集：按工具声明的场景过滤注册，包头注入业务上下文与收集器。 */
    private List<ToolCallback> toolsFor(ToolRun run) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (AgentTool tool : agentTools) {
            if (tool.scenes().contains(run.getScene())) {
                callbacks.add(new ToolCallbackAdapter(tool, run, aiProperties, toolExecutor));
            }
        }
        return callbacks;
    }

    /** Spring AI AgentToolContext 载荷：业务 AgentToolContext 与事件/配对收集器。 */
    private Map<String, Object> toolContextMap(ToolRun run) {
        Map<String, Object> context = new HashMap<>();
        context.put(ToolCallbackAdapter.KEY_TOOL_CONTEXT, run.getToolContext());
        context.put(ToolCallbackAdapter.KEY_TOOL_SINK, run.getSink());
        return context;
    }

    /** 场景解析出的供应商 → 可用 ChatModel（缺密钥回退脚本模型）。 */
    private ChatModel resolveModel(SceneModel sm) {
        String provider = sm.getProviderName();
        if (StrUtil.isBlank(provider)) {
            return scriptedChatModel;
        }
        if ("BAILIAN".equalsIgnoreCase(provider)) {
            return providerModel("BAILIAN", aiProperties.getBailianBaseUrl(), aiProperties.getBailianApiKey());
        }
        if ("DEEPSEEK".equalsIgnoreCase(provider)) {
            return providerModel("DEEPSEEK", aiProperties.getDeepseekBaseUrl(), aiProperties.getDeepseekApiKey());
        }
        log.warn("未知供应商 {}，回退脚本模型", provider);
        return scriptedChatModel;
    }

    /** 严格解析（连通性测试用）：缺失或未配置密钥直接报错。 */
    private ChatModel resolveModelStrict(String providerName) {
        if (StrUtil.isBlank(providerName)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "未知供应商：" + providerName);
        }
        String baseUrl;
        String apiKey;
        if ("BAILIAN".equalsIgnoreCase(providerName)) {
            baseUrl = aiProperties.getBailianBaseUrl();
            apiKey = aiProperties.getBailianApiKey();
        } else if ("DEEPSEEK".equalsIgnoreCase(providerName)) {
            baseUrl = aiProperties.getDeepseekBaseUrl();
            apiKey = aiProperties.getDeepseekApiKey();
        } else {
            throw new BizException(ErrorCode.INVALID_PARAM, "未知供应商：" + providerName);
        }
        if (StrUtil.isBlank(apiKey)) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "供应商未配置密钥：" + providerName);
        }
        return buildOpenAiChatModel(baseUrl, apiKey);
    }

    /** 供应商 ChatModel 懒装配缓存（同供应商同参数复用一个实例）。 */
    private ChatModel providerModel(String name, String baseUrl, String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            log.warn("供应商 {} 未配置密钥，回退脚本模型", name);
            return scriptedChatModel;
        }
        return modelCache.computeIfAbsent(name, n -> buildOpenAiChatModel(baseUrl, apiKey));
    }

    /** 构造 OpenAI 兼容 ChatModel（百炼 compatible-mode 与 DeepSeek 同协议；OpenAiChatModel builder 自建同步/异步客户端，官方路径）。 */
    private ChatModel buildOpenAiChatModel(String baseUrl, String apiKey) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    /** 逐请求 options：模型名按场景解析下发，temperature/maxTokens 可空。 */
    private OpenAiChatOptions openAiOptions(SceneModel sm, Double temperature, Integer maxTokens) {
        return openAiOptions(sm.getModel(), temperature, maxTokens);
    }

    /** 逐请求 options：显式模型名（专用模型路径），temperature/maxTokens 可空。 */
    private OpenAiChatOptions openAiOptions(String model, Double temperature, Integer maxTokens) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(model);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        return builder.build();
    }

    /** 从 ChatResponse 提取文本（无结果返回空串）。 */
    private String textOf(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getResults())) {
            return "";
        }
        AssistantMessage message = response.getResult().getOutput();
        return message == null || message.getText() == null ? "" : message.getText();
    }

    /** 从响应元数据提取 token 用量（供应商未返回时为 0，异常静默）。 */
    private int[] usageOf(ChatResponse response) {
        try {
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                return new int[]{Math.max(0, usage.getPromptTokens()), Math.max(0, usage.getCompletionTokens())};
            }
        } catch (Exception ignored) {
            // 元数据形态因供应商而异，缺失不阻断调用
        }
        return new int[]{0, 0};
    }

    /** 生效 System Prompt 的哈希前缀（前 12 位），用于调用版本溯源；无 System Prompt 返回空串。 */
    private String promptHash(List<Message> messages) {
        if (messages == null) {
            return "";
        }
        for (Message message : messages) {
            if (message instanceof SystemMessage systemMessage && systemMessage.getText() != null) {
                String sha1 = sha1Hex(systemMessage.getText());
                return sha1.length() > 12 ? sha1.substring(0, 12) : sha1;
            }
        }
        return "";
    }

    /** JDK SHA-1 十六进制（免额外依赖）。 */
    private String sha1Hex(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String circuitKey(SceneModel sm) {
        return (sm.getProviderName() == null ? "" : sm.getProviderName()).toUpperCase()
                + ":" + sm.getModel();
    }

    private void checkCircuit(String key) {
        CircuitState state = circuits.get(key);
        if (state != null && state.openUntil > System.currentTimeMillis()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, CIRCUIT_MESSAGE);
        }
    }

    private void recordSuccess(String key) {
        circuits.computeIfPresent(key, (k, s) -> {
            s.failures = 0;
            s.openUntil = 0;
            return s;
        });
    }

    private void recordFailure(String key) {
        CircuitState state = circuits.computeIfAbsent(key, k -> new CircuitState());
        state.failures++;
        if (state.failures >= FAILURE_THRESHOLD) {
            state.openUntil = System.currentTimeMillis() + OPEN_MILLIS;
            state.failures = 0;
            log.warn("供应商模型 {} 连续失败，熔断 {} 秒", key, OPEN_MILLIS / 1000);
        }
    }

    /** 异常信息脱敏截断。 */
    private String safeMessage(Throwable e) {
        String msg = e.getMessage();
        if (StrUtil.isBlank(msg)) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}