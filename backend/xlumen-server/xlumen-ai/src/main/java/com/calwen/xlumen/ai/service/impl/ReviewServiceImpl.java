package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.dto.ReviewRequestDTO;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskService;
import com.calwen.xlumen.ai.service.ReviewService;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * AI 审校服务实现（F-0604）：content 必填、模型异源校验（写作与审校不同源）、创建 REVIEWER 任务。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service("aiReviewService")
public class ReviewServiceImpl implements ReviewService {

    private final AiTaskService aiTaskService;
    private final SceneConfigService sceneConfigService;

    public ReviewServiceImpl(AiTaskService aiTaskService, SceneConfigService sceneConfigService) {
        this.aiTaskService = aiTaskService;
        this.sceneConfigService = sceneConfigService;
    }

    @Override
    public Long submit(ReviewRequestDTO dto) {
        Long workspaceId = WorkspaceContext.workspaceId();
        Long userId = WorkspaceContext.userId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (StrUtil.isBlank(content)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "待审校正文不能为空");
        }
        checkHeterogeneous(workspaceId);
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        String inputJson = JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("articleId", dto.getArticleId())
                .set("content", content)
                .set("title", title));
        String idempotencyKey = "review:" + hash(dto.getArticleId() + "|" + content);
        return aiTaskService.submit(workspaceId, userId, AiScene.REVIEWER, inputJson, idempotencyKey);
    }

    /** 模型异源校验：Writing 与 Reviewer 的 provider+model 相同则失败（审校独立性）。 */
    private void checkHeterogeneous(Long workspaceId) {
        SceneModel writing = sceneConfigService.resolve(workspaceId, AiScene.WRITING);
        SceneModel reviewer = sceneConfigService.resolve(workspaceId, AiScene.REVIEWER);
        String wp = writing.getProviderName() == null ? "" : writing.getProviderName().toUpperCase();
        String rp = reviewer.getProviderName() == null ? "" : reviewer.getProviderName().toUpperCase();
        if (wp.equals(rp) && Objects.equals(writing.getModel(), reviewer.getModel())) {
            throw new BizException(ErrorCode.CONFLICT, "审校模型必须与写作模型不同源");
        }
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
