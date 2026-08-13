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
 * chunk_seq/heading_anchor/chunk_text/visibility/title 元数据，支持 F-0405 溯源与 F-0407 权限过滤。
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
    public static String vectorId(Long articleId, Long version, int seq) {
        return articleId + "_" + version + "_" + seq;
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
            row.put(PRIMARY_FIELD, vectorId(request.getArticleId(), request.getVersion(), chunk.getSeq()));
            row.put(VECTOR_FIELD, chunk.getEmbedding() == null ? List.of() : chunk.getEmbedding());
            row.put("workspace_id", request.getWorkspaceId());
            row.put("article_id", request.getArticleId());
            row.put("version", request.getVersion());
            row.put("chunk_seq", chunk.getSeq());
            row.put("heading_anchor", StrUtil.blankToDefault(chunk.getHeadingAnchor(), ""));
            row.put("chunk_text", chunk.getChunkText());
            row.put("visibility", request.getVisibility());
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
    public void delete(Long workspaceId, Long articleId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", COLLECTION_NAME);
        body.put("dbName", properties.getMilvusDatabase());
        body.put("filter", "workspace_id == " + workspaceId + " && article_id == " + articleId);
        postJson("/v2/vectordb/entities/delete", body);
    }

    @Override
    public List<SearchResultDTO> search(List<Float> queryEmbedding, Long workspaceId, String visibilityScope,
                                        Long articleId, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
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
        body.put("filter", buildFilter(workspaceId, visibilityScope, articleId));
        JsonNode response = postJson("/v2/vectordb/entities/search", body);
        if (response == null) {
            return List.of();
        }
        return parseSearchResponse(response);
    }

    /** 构建检索过滤表达式：空间隔离 + 可见性范围 + 可选文章级过滤（F-0407）。 */
    private String buildFilter(Long workspaceId, String visibilityScope, Long articleId) {
        List<String> conditions = new ArrayList<>();
        conditions.add("workspace_id == " + workspaceId);
        if ("PUBLIC_ONLY".equals(visibilityScope)) {
            conditions.add("visibility == 1");
        }
        if (articleId != null) {
            conditions.add("article_id == " + articleId);
        }
        return String.join(" && ", conditions);
    }

    /** 解析 search 响应：data[0] 为命中列表，逐条映射为 SearchResultDTO（含溯源字段与分数）。 */
    private List<SearchResultDTO> parseSearchResponse(JsonNode response) {
        JsonNode data = response.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            return List.of();
        }
        JsonNode hits = data.get(0);
        if (hits == null || !hits.isArray()) {
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
                    .articleId(entity.has("article_id") ? entity.get("article_id").asLong() : null)
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

    /** 确保集合存在：创建集合 → 建近似索引 → 加载集合（幂等，失败仅告警降级）。 */
    private void ensureCollection(int dimension) {
        if (dimension <= 0) {
            return;
        }
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("collectionName", COLLECTION_NAME);
        createBody.put("dbName", properties.getMilvusDatabase());
        createBody.put("dimension", dimension);
        createBody.put("primaryFieldName", PRIMARY_FIELD);
        createBody.put("idType", "VarChar");
        createBody.put("vectorFieldName", VECTOR_FIELD);
        createBody.put("metricType", METRIC_COSINE);
        createBody.put("autoID", false);
        createBody.put("enableDynamicField", true);
        postJson("/v2/vectordb/collections/create", createBody);

        Map<String, Object> indexBody = new LinkedHashMap<>();
        indexBody.put("collectionName", COLLECTION_NAME);
        indexBody.put("dbName", properties.getMilvusDatabase());
        indexBody.put("indexParams", List.of(Map.of("fieldName", VECTOR_FIELD, "indexName", "vector_idx",
                "metricType", METRIC_COSINE, "indexType", "AUTOINDEX")));
        postJson("/v2/vectordb/indexes/create", indexBody);

        Map<String, Object> loadBody = new LinkedHashMap<>();
        loadBody.put("collectionName", COLLECTION_NAME);
        loadBody.put("dbName", properties.getMilvusDatabase());
        postJson("/v2/vectordb/collections/load", loadBody);
    }

    /** POST JSON 到 Milvus REST v2 端点；失败记录 warn 并返回 null（触发降级）。 */
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
                return JSON_MAPPER.readTree(response.body());
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
