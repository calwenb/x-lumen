package com.calwen.xlumen.publishing.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 语音化端点（公开）：把知识标题+摘要转语音（TTS）。
 * 百炼 TTS 不走 OpenAI 兼容模式（其无 /audio/speech，恒 404），须调 DashScope 原生
 * multimodal-generation 端点（默认 qwen3-tts-flash，profile xlumen.bailian.model-tts 可换）；
 * 响应为 JSON，音频落在 output.audio.url（OSS 临时 wav 地址），二次下载后回给前端。
 * 上游或密钥不可用时返回 501，前端友好提示。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/public/knowledge/{knowledgeId}/speech")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    private static final int TEXT_MAX = 500;

    /** TTS 模型：默认便宜档 qwen3-tts-flash（profile xlumen.bailian.model-tts 可换）。 */
    @Value("${xlumen.bailian.model-tts}")
    private String ttsModel;

    /** 发音人：qwen-tts 系列默认 Cherry（profile xlumen.tts-voice 可换）。 */
    @Value("${xlumen.tts-voice}")
    private String ttsVoice;

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    /** 百炼 TTS 原生端点（与 chat 的 compatible-mode base-url 不同源，环境无关，配在 base yml）。 */
    @Value("${xlumen.bailian.tts-url}")
    private String ttsUrl;

    @Value("${xlumen.bailian.api-key}")
    private String bailianApiKey;

    @GetMapping
    public ResponseEntity<byte[]> speak(@PathVariable Long knowledgeId) {
        if (StrUtil.isBlank(bailianApiKey)) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "语音能力未配置，请先填写 AI 密钥");
        }
        List<Long> visible = knowledgeApi.resolveVisibleKbIds(WorkspaceContext.userId());
        if (visible == null || visible.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在或未公开");
        }
        KnowledgeDetailDTO knowledge = contentApi.getPublished(null, knowledgeId, visible);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在或未公开");
        }
        String text = buildText(knowledge.getTitle(), knowledge.getSummary());
        try {
            byte[] audio = synthesize(text);
            if (audio != null) {
                return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(audio);
            }
        } catch (Exception e) {
            log.warn("TTS 调用失败（语音能力降级不可用）", e);
        }
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "语音功能暂不可用，请稍后再试");
    }

    /**
     * 调 DashScope 原生 multimodal-generation 合成语音，成功后跟随 output.audio.url
     * 下载 wav 字节；上游非 2xx、无音频地址或下载失败返回 null（由调用方降级 501）。
     */
    private byte[] synthesize(String text) throws Exception {
        JSONObject body = JSONUtil.createObj()
                .set("model", ttsModel)
                .set("input", JSONUtil.createObj().set("text", text).set("voice", ttsVoice));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ttsUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bailianApiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("TTS 服务返回异常 HTTP={} body={}", response.statusCode(),
                    StrUtil.maxLength(response.body(), 200));
            return null;
        }
        String audioUrl = JSONUtil.parseObj(response.body())
                .getByPath("output.audio.url", String.class);
        if (StrUtil.isBlank(audioUrl)) {
            log.warn("TTS 响应缺少音频地址 body={}", StrUtil.maxLength(response.body(), 200));
            return null;
        }
        HttpResponse<byte[]> audio = client.send(HttpRequest.newBuilder(URI.create(audioUrl))
                .timeout(Duration.ofSeconds(30))
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (audio.statusCode() < 200 || audio.statusCode() >= 300) {
            log.warn("TTS 音频下载失败 HTTP={}", audio.statusCode());
            return null;
        }
        return audio.body();
    }

    private String buildText(String title, String summary) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            sb.append(title).append("。");
        }
        sb.append(StrUtil.blankToDefault(summary, ""));
        String text = sb.toString().trim();
        return text.length() > TEXT_MAX ? text.substring(0, TEXT_MAX) : text;
    }
}