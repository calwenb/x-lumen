package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.config.KnowledgeAiProperties;
import com.calwen.xlumen.knowledge.service.EmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化服务实现（F-0402/F-0404）：JDK HttpClient 调百炼兼容 embeddings 端点，
 * Jackson 3（tools.jackson）解析 data[].embedding，32 片/批。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    /** 批大小：32 片/批。 */
    private static final int BATCH_SIZE = 32;
    private static final String UNAVAILABLE_MSG = "向量服务不可用";
    private static final JsonMapper JSON_MAPPER = new JsonMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Resource
    private KnowledgeAiProperties properties;

    @Override
    public List<List<Float>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String apiKey = properties.getBailianApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
        }
        List<List<Float>> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            result.addAll(embedBatch(apiKey, batch));
        }
        return result;
    }

    /** 单批调用百炼 embeddings 端点并解析向量。 */
    private List<List<Float>> embedBatch(String apiKey, List<String> batch) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getBailianModelEmbedding());
            body.put("input", batch);
            body.put("encoding_format", "float");
            String json = JSON_MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingsUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Embedding 调用失败：HTTP={}, 响应={}", response.statusCode(), response.body());
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
            }
            return parseEmbeddings(response.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding 调用异常", e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
        }
    }

    /** 解析 data[].embedding 为向量列表（与入参批次同序）。 */
    private List<List<Float>> parseEmbeddings(String responseBody) {
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(responseBody);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
        }
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
        }
        List<List<Float>> embeddings = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) {
                vector.add((float) value.asDouble());
            }
            embeddings.add(vector);
        }
        return embeddings;
    }

    private String embeddingsUrl() {
        String base = properties.getBailianBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/embeddings";
    }
}
