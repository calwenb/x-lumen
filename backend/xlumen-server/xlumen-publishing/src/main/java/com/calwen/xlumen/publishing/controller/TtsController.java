package com.calwen.xlumen.publishing.controller;

import cn.hutool.core.util.StrUtil;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 语音化端点（公开）：把知识标题+摘要转语音（TTS）。
 * 调用百炼 OpenAI 兼容 /audio/speech（qwen-tts）；端点或密钥不可用时返回 501，前端友好提示。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/public/knowledge/{knowledgeId}/speech")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    private static final String TTS_MODEL = "qwen-tts";
    private static final String TTS_VOICE = "Cherry";
    private static final int TEXT_MAX = 500;

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    @Value("${XLUMEN_BAILIAN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String bailianBaseUrl;

    @Value("${XLUMEN_BAILIAN_API_KEY:}")
    private String bailianApiKey;

    @GetMapping(produces = "audio/mpeg")
    public byte[] speak(@PathVariable Long knowledgeId) {
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bailianBaseUrl + "/audio/speech"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + bailianApiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"model\":\"" + TTS_MODEL + "\",\"input\":\"" + jsonEscape(text)
                                    + "\",\"voice\":\"" + TTS_VOICE + "\"}"))
                    .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log.warn("TTS 服务返回异常 HTTP={}", response.statusCode());
        } catch (Exception e) {
            log.warn("TTS 调用失败（语音能力降级不可用）", e);
        }
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "语音功能暂不可用，请稍后再试");
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

    private String jsonEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}