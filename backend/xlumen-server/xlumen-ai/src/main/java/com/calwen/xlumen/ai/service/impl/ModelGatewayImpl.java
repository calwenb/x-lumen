package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ModelProvider;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 模型网关实现（F-0501/F-0502）：场景解析、供应商回退（缺密钥→Mock）、简单熔断（连续失败 5 次熔断 60s）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ModelGatewayImpl implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayImpl.class);

    /** 连续失败熔断阈值。 */
    private static final int FAILURE_THRESHOLD = 5;
    /** 熔断打开时长（毫秒）。 */
    private static final long OPEN_MILLIS = 60_000L;
    /** 对外统一熔断提示。 */
    private static final String CIRCUIT_MESSAGE = "AI 服务暂时不可用，请稍后重试";

    private final Map<String, ModelProvider> providers;
    private final SceneConfigService sceneConfigService;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public ModelGatewayImpl(List<ModelProvider> providerList, SceneConfigService sceneConfigService) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(p -> p.name().toUpperCase(), p -> p, (a, b) -> a));
        this.sceneConfigService = sceneConfigService;
    }

    @Override
    public SceneModel resolveScene(Long workspaceId, AiScene scene) {
        return sceneConfigService.resolve(workspaceId, scene);
    }

    @Override
    public String chat(Long workspaceId, AiScene scene, ProviderChatRequest request) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ModelProvider provider = resolveProvider(sm.getProviderName());
        String key = circuitKey(sm);
        checkCircuit(key);
        request.setModel(sm.getModel());
        try {
            String result = provider.chat(request);
            recordSuccess(key);
            return result;
        } catch (Exception e) {
            recordFailure(key);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, CIRCUIT_MESSAGE);
        }
    }

    @Override
    public void chatStream(Long workspaceId, AiScene scene, ProviderChatRequest request,
                           Consumer<String> onChunk, Consumer<Throwable> onError) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ModelProvider provider = resolveProvider(sm.getProviderName());
        String key = circuitKey(sm);
        try {
            checkCircuit(key);
        } catch (BizException e) {
            onError.accept(e);
            return;
        }
        request.setModel(sm.getModel());
        AtomicBoolean errored = new AtomicBoolean(false);
        provider.chatStream(request, onChunk, err -> {
            errored.set(true);
            recordFailure(key);
            onError.accept(err);
        });
        if (!errored.get()) {
            recordSuccess(key);
        }
    }

    @Override
    public List<Float> embed(Long workspaceId, AiScene scene, String text) {
        SceneModel sm = resolveScene(workspaceId, scene);
        ModelProvider provider = resolveProvider(sm.getProviderName());
        String key = circuitKey(sm);
        checkCircuit(key);
        try {
            List<Float> result = provider.embed(text);
            recordSuccess(key);
            return result;
        } catch (Exception e) {
            recordFailure(key);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, CIRCUIT_MESSAGE);
        }
    }

    @Override
    public boolean test(String providerName, String model) {
        ModelProvider provider = resolveProviderStrict(providerName);
        ProviderChatRequest request = ProviderChatRequest.builder()
                .model(model)
                .messages(List.of(ChatMessage.builder().role("user").content("ping").build()))
                .temperature(0.0)
                .maxTokens(16)
                .stream(false)
                .build();
        try {
            String resp = provider.chat(request);
            return StrUtil.isNotBlank(resp);
        } catch (Exception e) {
            log.warn("模型连通性测试失败 provider={} model={}", providerName, model, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "连接失败：" + safeMessage(e));
        }
    }

    /** 按供应商名解析；缺失或不含密钥时回退 MockProvider（log warn）。 */
    private ModelProvider resolveProvider(String name) {
        ModelProvider provider = providers.get(name == null ? "" : name.toUpperCase());
        if (provider == null || !provider.available()) {
            log.warn("供应商 {} 不可用（缺失密钥），回退 MockProvider", name);
            return providers.get("MOCK");
        }
        return provider;
    }

    /** 严格解析（连通性测试用）：缺失或不含密钥直接报错。 */
    private ModelProvider resolveProviderStrict(String name) {
        ModelProvider provider = providers.get(name == null ? "" : name.toUpperCase());
        if (provider == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "未知供应商：" + name);
        }
        if (!provider.available()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "供应商未配置密钥：" + name);
        }
        return provider;
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

    /** 熔断状态：连续失败计数与打开截止时间。 */
    private static class CircuitState {
        private int failures;
        private long openUntil;
    }
}
