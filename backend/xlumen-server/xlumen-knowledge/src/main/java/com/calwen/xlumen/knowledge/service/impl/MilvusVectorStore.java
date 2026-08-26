package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.config.MilvusProperties;
import com.calwen.xlumen.knowledge.dto.Chunk;
import com.calwen.xlumen.knowledge.service.VectorStore;
import lombok.extern.slf4j.Slf4j;
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
 * Milvus 向量库实现（REST API v2，HTTP + JSON，不引 SDK，避免 JDK25/Boot4 兼容风险）。
 * 集合 kb_chunks 以 id(VarChar 主键)+vector(向量) 为核心，附带 workspace_id/article_id/version/
 * chunk_seq/heading_anchor/chunk_text/visibility/title/kb_id 元数据，支持 溯源与
 * 按库过滤（决策 D13）。注意：article_id 为 KB-1 遗留 schema 字段名（本任务不改名，
 * Milvus 就绪后同步 schema 为 knowledge_id）；kb_id 依赖 enableDynamicField 动态字段写入。
 * 任一请求失败时记录 warn 并降级（index/delete 跳过、search 返回空），由装配层探测不可达时整体回退 Noop。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
public class MilvusVectorStore implements VectorStore {

    /** 向量集合名（与 kb_index_version.index_name 保持一致）。 */
    public static final String COLLECTION_NAME = "kb_chunks";
    private static final String VECTOR_FIELD = "vector";
    private static final String PRIMARY_FIELD = "id";
    private static final String METRIC_COSINE = "COSINE";
    private static final JsonMapper JSON_MAPPER = new JsonMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final MilvusProperties properties;

    public MilvusVectorStore(MilvusProperties properties) {
        this.properties = properties;
    }

    /** 生成向量条目主键（与 kb_chunk.vector_id 保持一致）。 */
    public static String vectorId(Long knowledgeId, Long version, int seq) {
        return knowledgeId + "_" + version + "_" + seq;
    }

    @Override
    public void index(IndexRequestDTO request, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        int dimension = chunks.get(0).getEmbedding() == null ? 0 : chunks.get(0).getEmbedding().size();
        ensureCollection(dimension);
        List<Map<String, Object>> rows = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, Object> row = new LinkedHashMap<>();
            // 主键：字符串向量 ID 的 52 位哈希（本环境 REST 为 Int64 主键且 JSON 数值按 float64 处理，
            // 超过 2^53 会失真；52 位主键精确无碰撞风险）
            row.put(PRIMARY_FIELD, pkValue(vectorId(request.getKnowledgeId(), request.getVersion(), chunk.getSeq())));
            row.put(VECTOR_FIELD, chunk.getEmbedding() == null ? List.of() : chunk.getEmbedding());
            // 大整数（雪花 ID）一律以字符串写入动态字段：本环境 JSON 数值经 float64 会破坏 >2^53 精度，
            // 字符串形式精确无损；过滤器同步用字符串字面量（buildFilter）
            row.put("workspace_id", String.valueOf(request.getWorkspaceId()));
            row.put("article_id", String.valueOf(request.getKnowledgeId()));
            // 决策 D13 检索按库过滤（kb_id in [...]）；legacy visibility 字段不再写入
            //（KB-1 遗留 schema，Milvus 就绪后同步移除）
            row.put("kb_id", String.valueOf(request.getKbId()));
            row.put("version", request.getVersion());
            row.put("chunk_seq", chunk.getSeq());
            row.put("heading_anchor", StrUtil.blankToDefault(chunk.getHeadingAnchor(), ""));
            row.put("chunk_text", chunk.getChunkText());
            row.put("title", StrUtil.blankToDefault(request.getTitle(), ""));
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", COLLECTION_NAME);
        body.put("dbName", properties.getMilvusDatabase());
        body.put("data", rows);
        postJson("/v2/vectordb/entities/insert", body);
    }

    @Override
    public void delete(Long workspaceId, Long knowledgeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", COLLECTION_NAME);
        body.put("dbName", properties.getMilvusDatabase());
        body.put("filter", "workspace_id == \"" + workspaceId + "\" && article_id == \"" + knowledgeId + "\"");
        postJson("/v2/vectordb/entities/delete", body);
    }

    @Override
    public List<SearchResultDTO> search(List<Float> queryEmbedding, Long workspaceId, List<Long> kbIds,
                                        Long knowledgeId, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", COLLECTION_NAME);
        body.put("dbName", properties.getMilvusDatabase());
        body.put("data", List.of(queryEmbedding));
        body.put("annsField", VECTOR_FIELD);
        body.put("limit", Math.max(1, topK));
        body.put("outputFields", List.of("article_id", "title", "chunk_seq", "heading_anchor", "chunk_text", "visibility"));
        body.put("searchParams", Map.of("metricType", METRIC_COSINE, "params", Map.of()));
        body.put("filter", buildFilter(workspaceId, kbIds, knowledgeId));
        JsonNode response = postJson("/v2/vectordb/entities/search", body);
        if (response == null) {
            return List.of();
        }
        return parseSearchResponse(response);
    }

    /** 构建检索过滤表达式：空间隔离（workspaceId 为空时省略，用于跨空间可见库聚合检索）+ 可见库集合（kb_id in [...]，决策 D13）+ 可选知识级过滤。
     *  大整数 ID 以字符串字面量参与比较（写入端已字符串化，规避本环境 float64 精度失真）。 */
    static String buildFilter(Long workspaceId, List<Long> kbIds, Long knowledgeId) {
        List<String> conditions = new ArrayList<>();
        if (workspaceId != null) {
            conditions.add("workspace_id == \"" + workspaceId + "\"");
        }
        String kbIdsExpr = kbIds.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(", "));
        conditions.add("kb_id in [" + kbIdsExpr + "]");
        if (knowledgeId != null) {
            conditions.add("article_id == \"" + knowledgeId + "\"");
        }
        return String.join(" && ", conditions);
    }

    /** 解析 search 响应：兼容扁平（data=[命中...]）与嵌套（data[0]=[命中...]）两种返回形态，
     *  命中元素字段直接平铺或包在 entity 内；逐条映射为 SearchResultDTO（含溯源字段与分数）。 */
    private List<SearchResultDTO> parseSearchResponse(JsonNode response) {
        JsonNode data = response.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            return List.of();
        }
        JsonNode hits = data.get(0).isArray() ? data.get(0) : data;
        if (hits == null || !hits.isArray() || hits.isEmpty()) {
            return List.of();
        }
        List<SearchResultDTO> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode entity = hit.get("entity");
            if (entity == null || entity.isMissingNode()) {
                entity = hit;
            }
            double distance = hit.has("distance") ? hit.get("distance").asDouble() : 0.0;
            results.add(SearchResultDTO.builder()
                    .knowledgeId(entity.has("article_id") ? entity.get("article_id").asLong() : null)
                    .title(entity.has("title") ? entity.get("title").asText() : "")
                    .chunkSeq(entity.has("chunk_seq") ? entity.get("chunk_seq").asInt() : 0)
                    .headingAnchor(entity.has("heading_anchor") ? entity.get("heading_anchor").asText() : "")
                    .chunkText(entity.has("chunk_text") ? entity.get("chunk_text").asText() : "")
                    .score((float) distance)
                    .visibility(entity.has("visibility") ? entity.get("visibility").asInt() : 1)
                    .build());
        }
        return results;
    }

    /** 确保集合存在（幂等）：本环境 REST 仅支持快速建集（dimension 驱动，自动建索引并加载），
     *  业务元数据以动态字段承载（enableDynamicField 默认开启）。 */
    private void ensureCollection(int dimension) {
        if (dimension <= 0) {
            return;
        }
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("collectionName", COLLECTION_NAME);
        createBody.put("dbName", properties.getMilvusDatabase());
        createBody.put("dimension", dimension);
        createBody.put("metricType", METRIC_COSINE);
        postJson("/v2/vectordb/collections/create", createBody);
    }

    /** 字符串向量 ID 的确定性主键（FNV-1a 低 52 位，精确区间内）：兼容 REST 快速建集的 Int64 主键约束。 */
    private static long pkValue(String vectorId) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < vectorId.length(); i++) {
            hash ^= vectorId.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash & 0xFFFFFFFFFFFFFL;
    }

    /** POST JSON 到 Milvus REST v2 端点；HTTP 非 2xx 或响应体 code 非 0 时记录 warn 并返回 null（触发降级）。 */
    private JsonNode postJson(String path, Map<String, Object> body) {
        try {
            String json = JSON_MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode parsed = JSON_MAPPER.readTree(response.body());
                if (parsed.has("code") && parsed.get("code").asInt() != 0) {
                    log.warn("Milvus REST 返回错误：path={}, code={}, message={}", path,
                            parsed.get("code").asInt(),
                            parsed.has("message") ? parsed.get("message").asText() : "");
                    return null;
                }
                return parsed;
            }
            log.warn("Milvus REST 调用失败：path={}, HTTP={}", path, response.statusCode());
        } catch (Exception e) {
            log.warn("Milvus REST 调用异常，降级处理：path={}, reason={}", path, e.getMessage());
        }
        return null;
    }

    private String baseUrl() {
        return "http://" + properties.getMilvusHost() + ":" + properties.getMilvusPort();
    }
}
