package com.calwen.xlumen.ai.service.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * OpenAI 兼容供应商抽象基类（F-0501）：JDK HttpClient 调用 /chat/completions 与 /embeddings。
 * 流式用 BodyHandlers.ofInputStream 手动解析 SSE（data: 行、choices[0].delta.content 增量、[DONE] 结束）。
 * connectTimeout 5s，非流式 requestTimeout 30s。
 * IDEA-025 F-0708：三种协议扩展——请求侧 tools/assistant.tool_calls/tool 角色消息序列化；
 * 响应侧 chat() 解析 message.tool_calls + finish_reason，流式按 index 归并 delta.tool_calls 分片并回调终态。
 *
 * @author calwen
 * @date 2026/8/13
 */
public abstract class OpenAICompatibleProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    /** 连接超时（决策 D8 下供应商响应约定）。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 非流式请求超时。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 供应商 Base URL。 */
    protected final String baseUrl;
    /** 供应商 API Key（不入日志）。 */
    protected final String apiKey;
    /** 向量化模型（DeepSeek 不支持，可为空）。 */
    protected final String embeddingModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    protected OpenAICompatibleProvider(String baseUrl, String apiKey, String embeddingModel) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public boolean available() {
        return StrUtil.isNotBlank(apiKey);
    }

    @Override
    public ProviderChatResult chat(ProviderChatRequest request) {
        String body = buildChatBody(request, false).toString();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("供应商返回 " + response.statusCode() + "：" + truncate(response.body()));
            }
            JSONObject obj = JSONUtil.parseObj(response.body());
            JSONObject message = obj.getByPath("choices[0].message", JSONObject.class);
            String content = message == null ? null : message.getStr("content");
            List<ToolCall> toolCalls = new ArrayList<>();
            if (message != null) {
                JSONArray calls = message.getJSONArray("tool_calls");
                if (calls != null) {
                    for (Object o : calls) {
                        if (!(o instanceof JSONObject c)) {
                            continue;
                        }
                        JSONObject fn = c.getJSONObject("function");
                        toolCalls.add(ToolCall.builder()
                                .id(c.getStr("id"))
                                .name(fn == null ? null : fn.getStr("name"))
                                .arguments(fn == null ? null : fn.getStr("arguments"))
                                .build());
                    }
                }
            }
            String finishReason = obj.getByPath("choices[0].finish_reason", String.class);
            return ProviderChatResult.builder()
                    .content(content == null ? "" : content)
                    .toolCalls(toolCalls)
                    .finishReason(finishReason)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("供应商调用失败：" + safeMessage(e), e);
        }
    }

    @Override
    public void chatStream(ProviderChatRequest request, StreamCallback callback, Consumer<Throwable> onError) {
        String body = buildChatBody(request, true).toString();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("供应商返回 " + response.statusCode());
            }
            StreamAccumulator acc = new StreamAccumulator();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    if (data.isEmpty()) {
                        continue;
                    }
                    try {
                        parseDelta(data, acc, delta -> callback.onContent(delta));
                    } catch (Exception ignore) {
                        log.debug("跳过无法解析的 SSE 行：{}", data);
                    }
                }
            }
            callback.onResult(acc.toResult());
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    /**
     * 解析单行 SSE data 并聚合到累加器：delta.content 实时回调；delta.tool_calls 按 index 归并；
     * finish_reason 暂存。包级可见，供流式 tool_calls 分片序列单测直测。
     */
    static void parseDelta(String data, StreamAccumulator acc, Consumer<String> onContent) {
        JSONObject obj = JSONUtil.parseObj(data);
        JSONObject delta = obj.getByPath("choices[0].delta", JSONObject.class);
        if (delta != null) {
            String content = delta.getStr("content");
            if (StrUtil.isNotBlank(content)) {
                acc.appendContent(content);
                onContent.accept(content);
            }
            JSONArray toolCalls = delta.getJSONArray("tool_calls");
            if (toolCalls != null) {
                for (Object o : toolCalls) {
                    if (o instanceof JSONObject call) {
                        mergeToolCallDelta(call, acc);
                    }
                }
            }
        }
        String finish = obj.getByPath("choices[0].finish_reason", String.class);
        if (StrUtil.isNotBlank(finish)) {
            acc.setFinishReason(finish);
        }
    }

    /**
     * 按 index 归并流式 tool_calls 分片：id/name 首现捕获、arguments 分片追加（包级可见供单测直测）。
     */
    static void mergeToolCallDelta(JSONObject call, StreamAccumulator acc) {
        Integer index = call.getInt("index");
        if (index == null) {
            index = 0;
        }
        ToolCall current = acc.getToolCall(index);
        String id = call.getStr("id");
        if (StrUtil.isNotBlank(id) && current.getId() == null) {
            current.setId(id);
        }
        JSONObject fn = call.getJSONObject("function");
        if (fn != null) {
            String name = fn.getStr("name");
            if (StrUtil.isNotBlank(name) && current.getName() == null) {
                current.setName(name);
            }
            String args = fn.getStr("arguments");
            if (StrUtil.isNotBlank(args)) {
                current.setArguments((current.getArguments() == null ? "" : current.getArguments()) + args);
            }
        }
    }

    /** 流式累加器：内容 + 按 index 有序聚合的 toolCalls + finishReason。 */
    static class StreamAccumulator {

        private final StringBuilder content = new StringBuilder();
        private final Map<Integer, ToolCall> toolCalls = new TreeMap<>();
        private String finishReason;

        void appendContent(String delta) {
            content.append(delta);
        }

        void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        ToolCall getToolCall(int index) {
            return toolCalls.computeIfAbsent(index, i -> ToolCall.builder().index(i).build());
        }

        ProviderChatResult toResult() {
            return ProviderChatResult.builder()
                    .content(content.toString())
                    .toolCalls(new ArrayList<>(toolCalls.values()))
                    .finishReason(finishReason)
                    .build();
        }
    }

    @Override
    public List<Float> embed(String text) {
        if (StrUtil.isBlank(embeddingModel)) {
            throw new UnsupportedOperationException(name() + " 不支持向量化");
        }
        JSONObject body = new JSONObject();
        body.set("model", embeddingModel);
        body.set("input", text);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("供应商返回 " + response.statusCode() + "：" + truncate(response.body()));
            }
            JSONObject obj = JSONUtil.parseObj(response.body());
            JSONArray arr = obj.getByPath("data[0].embedding", JSONArray.class);
            List<Float> result = new ArrayList<>();
            for (Object o : arr) {
                result.add(((Number) o).floatValue());
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("向量化调用失败：" + safeMessage(e), e);
        }
    }

    /** 组装 OpenAI 兼容对话请求体：tools/assistant.tool_calls/tool 角色消息（IDEA-025 F-0708）。包级可见供单测快照断言。 */
    JSONObject buildChatBody(ProviderChatRequest request, boolean stream) {
        JSONObject body = new JSONObject();
        body.set("model", request.getModel());
        JSONArray messages = new JSONArray();
        if (request.getMessages() != null) {
            for (ChatMessage m : request.getMessages()) {
                JSONObject msg = new JSONObject().set("role", m.getRole()).set("content", m.getContent());
                if ("assistant".equals(m.getRole()) && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    JSONArray calls = new JSONArray();
                    for (ToolCall call : m.getToolCalls()) {
                        calls.add(new JSONObject()
                                .set("id", call.getId())
                                .set("type", "function")
                                .set("function", new JSONObject()
                                        .set("name", call.getName())
                                        .set("arguments", call.getArguments() == null ? "" : call.getArguments())));
                    }
                    msg.set("tool_calls", calls);
                }
                if ("tool".equals(m.getRole())) {
                    if (m.getToolCallId() != null) {
                        msg.set("tool_call_id", m.getToolCallId());
                    }
                    if (m.getName() != null) {
                        msg.set("name", m.getName());
                    }
                }
                messages.add(msg);
            }
        }
        body.set("messages", messages);
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            JSONArray tools = new JSONArray();
            for (ToolSpec spec : request.getTools()) {
                JSONObject fn = new JSONObject()
                        .set("name", spec.getName())
                        .set("description", spec.getDescription());
                if (StrUtil.isNotBlank(spec.getParameters())) {
                    try {
                        fn.set("parameters", JSONUtil.parseObj(spec.getParameters()));
                    } catch (Exception e) {
                        fn.set("parameters", spec.getParameters());
                    }
                }
                tools.add(new JSONObject().set("type", "function").set("function", fn));
            }
            body.set("tools", tools);
        }
        if (request.getTemperature() != null) {
            body.set("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.set("max_tokens", request.getMaxTokens());
        }
        body.set("stream", stream);
        return body;
    }

    /** 截断响应文本，避免日志/异常过长。 */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
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