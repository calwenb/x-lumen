package com.calwen.xlumen.knowledge.config;

import com.calwen.xlumen.knowledge.service.VectorStore;
import com.calwen.xlumen.knowledge.service.impl.MilvusVectorStore;
import com.calwen.xlumen.knowledge.service.impl.NoopVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 向量库装配决策（F-0402）：启动时探测 Milvus REST v2 可达性（超时 2s），
 * 可达启用 MilvusVectorStore，否则降级 NoopVectorStore（@Bean + if/else 选择）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({MilvusProperties.class, KnowledgeAiProperties.class})
public class VectorStoreAutoConfiguration {

    /** Milvus 健康探测超时（毫秒）。 */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    /**
     * 按可达性装配向量库实现（唯一 VectorStore Bean）。
     *
     * @param milvusProperties Milvus 连接配置
     * @return 可达为 MilvusVectorStore，否则 NoopVectorStore
     */
    @Bean
    public VectorStore vectorStore(MilvusProperties milvusProperties) {
        if (milvusReachable(milvusProperties)) {
            log.info("Milvus 可达（{}:{}），启用 MilvusVectorStore",
                    milvusProperties.getMilvusHost(), milvusProperties.getMilvusPort());
            return new MilvusVectorStore(milvusProperties);
        }
        log.warn("Milvus 不可达（{}:{}），降级为 NoopVectorStore（仅索引元数据，不写向量）",
                milvusProperties.getMilvusHost(), milvusProperties.getMilvusPort());
        return new NoopVectorStore();
    }

    /**
     * 探测 Milvus REST v2（BUG-004）：/healthz 位于 metrics 端口（9091），打 19530 恒 404 导致恒降级；
     * 改打与 MilvusVectorStore 数据面一致的 REST v2 端点 collections/has（集合存在与否均返回 200），
     * 200 视为可达，其余或异常视为不可达。
     */
    private boolean milvusReachable(MilvusProperties props) {
        try {
            String body = "{\"collectionName\":\"" + MilvusVectorStore.COLLECTION_NAME
                    + "\",\"dbName\":\"" + props.getMilvusDatabase() + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + props.getMilvusHost() + ":" + props.getMilvusPort()
                            + "/v2/vectordb/collections/has"))
                    .timeout(PROBE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
