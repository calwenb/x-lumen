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
import java.util.function.Consumer;

/**
 * OpenAI 兼容供应商抽象基类（F-0501）：JDK HttpClient 调用 /chat/completions 与 /embeddings。
 * 流式用 BodyHandlers.ofInputStream 手动解析 SSE（data: 行、choices[0].delta.content 增量、[DONE] 结束）。
 * connectTimeout 5s，非流式 requestTimeout 30s。
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
    public String chat(ProviderChatRequest request) {
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
            String content = obj.getByPath("choices[0].message.content", String.class);
            return content == null ? "" : content;
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
    public void chatStream(ProviderChatRequest request, Consumer<String> onChunk, Consumer<Throwable> onError) {
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
                        JSONObject obj = JSONUtil.parseObj(data);
                        String delta = obj.getByPath("choices[0].delta.content", String.class);
                        if (StrUtil.isNotBlank(delta)) {
                            onChunk.accept(delta);
                        }
                    } catch (Exception ignore) {
                        log.debug("跳过无法解析的 SSE 行：{}", data);
                    }
                }
            }
        } catch (Exception e) {
            onError.accept(e);
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

    /** 组装 OpenAI 兼容对话请求体。 */
    private JSONObject buildChatBody(ProviderChatRequest request, boolean stream) {
        JSONObject body = new JSONObject();
        body.set("model", request.getModel());
        JSONArray messages = new JSONArray();
        if (request.getMessages() != null) {
            for (ChatMessage m : request.getMessages()) {
                messages.add(new JSONObject().set("role", m.getRole()).set("content", m.getContent()));
            }
        }
        body.set("messages", messages);
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
