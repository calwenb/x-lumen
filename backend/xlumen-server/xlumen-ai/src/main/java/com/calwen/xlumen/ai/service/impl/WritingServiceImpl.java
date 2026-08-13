package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.dto.WritingRequestDTO;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskService;
import com.calwen.xlumen.ai.service.WritingService;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * AI 写作服务实现（F-0601）：topic/draft/content 三选一校验，幂等键取内容 SHA-256。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class WritingServiceImpl implements WritingService {

    private final AiTaskService aiTaskService;

    public WritingServiceImpl(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @Override
    public Long submit(WritingRequestDTO dto) {
        Long workspaceId = WorkspaceContext.workspaceId();
        Long userId = WorkspaceContext.userId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String topic = trim(dto.getTopic());
        String draft = trim(dto.getDraft());
        String content = trim(dto.getContent());
        String title = trim(dto.getTitle());
        if (StrUtil.isBlank(topic) && StrUtil.isBlank(draft) && StrUtil.isBlank(content)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "主题、草稿、素材至少填写一项");
        }
        String inputJson = JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("topic", topic)
                .set("draft", draft)
                .set("content", content)
                .set("title", title));
        String idempotencyKey = "writing:" + hash(topic + "|" + draft + "|" + content + "|" + title);
        return aiTaskService.submit(workspaceId, userId, AiScene.WRITING, inputJson, idempotencyKey);
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** 内容 SHA-256（hutool-core 不含 DigestUtil，用 JDK 原生）。 */
    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
