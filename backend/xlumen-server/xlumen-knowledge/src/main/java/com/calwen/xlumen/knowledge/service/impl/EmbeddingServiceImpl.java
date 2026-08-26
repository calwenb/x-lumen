package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.config.KnowledgeAiProperties;
import com.calwen.xlumen.knowledge.service.EmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务实现（D20 全量迁移）：手写 HTTP 层改由 Spring AI OpenAiEmbeddingModel
 * （builder 依 options 自建 OpenAI SDK 客户端，与 ai 模块 ChatModel 同一构造方式），32 片/批语义与错误语义不变——
 * key 缺失或调用失败抛 BizException(SERVICE_UNAVAILABLE)，由索引流水线标记任务失败。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    /** 批大小：32 片/批。 */
    private static final int BATCH_SIZE = 32;
    /** 客户端请求超时。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    /** 客户端重试次数（与 ai 模块一致）。 */
    private static final int MAX_RETRIES = 2;
    private static final String UNAVAILABLE_MSG = "向量服务不可用";

    @Resource
    private KnowledgeAiProperties properties;

    /** 懒装配缓存：首次有密钥调用时构造。 */
    private volatile OpenAiEmbeddingModel embeddingModel;

    @Override
    public List<List<Float>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String apiKey = properties.getBailianApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
        }
        OpenAiEmbeddingModel model = resolveModel();
        List<List<Float>> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            result.addAll(embedBatch(model, batch));
        }
        return result;
    }

    /** 单批调用 embeddings 端点并解析向量（与入参批次同序）。 */
    private List<List<Float>> embedBatch(OpenAiEmbeddingModel model, List<String> batch) {
        try {
            EmbeddingResponse response = model.call(new EmbeddingRequest(batch, model.getOptions()));
            List<List<Float>> embeddings = new ArrayList<>(response.getResults().size());
            for (var item : response.getResults()) {
                float[] output = item.getOutput();
                List<Float> vector = new ArrayList<>(output.length);
                for (float value : output) {
                    vector.add(value);
                }
                embeddings.add(vector);
            }
            return embeddings;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding 调用异常", e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MSG);
        }
    }

    private OpenAiEmbeddingModel resolveModel() {
        OpenAiEmbeddingModel local = embeddingModel;
        if (local == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    embeddingModel = buildModel();
                }
                local = embeddingModel;
            }
        }
        return local;
    }

    /** builder 依 options 自建 OpenAI SDK 客户端：baseUrl/apiKey/model/timeout/maxRetries 来自 KnowledgeAiProperties。 */
    private OpenAiEmbeddingModel buildModel() {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(properties.getBailianBaseUrl())
                .apiKey(properties.getBailianApiKey())
                .model(properties.getBailianModelEmbedding())
                .timeout(TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
        return OpenAiEmbeddingModel.builder().options(options).build();
    }
}