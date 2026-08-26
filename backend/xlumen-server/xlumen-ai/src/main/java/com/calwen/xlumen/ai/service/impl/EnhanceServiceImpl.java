package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.calwen.xlumen.ai.dto.EnhanceRequestDTO;
import com.calwen.xlumen.ai.entity.AiEnhanceResultEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.AiEnhanceResultMapper;
import com.calwen.xlumen.ai.prompt.PromptTemplates;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.EnhanceService;
import com.calwen.xlumen.ai.util.AiJson;
import com.calwen.xlumen.ai.vo.EnhanceResultVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 增值服务实现：SUMMARY/SEO 同步生成，结构化校验后落 ai_enhance_result；
 * generateAndStoreSummary 供发布事件监听（KnowledgePublishedSummaryListener）异步复用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class EnhanceServiceImpl implements EnhanceService {

    private final ChatRuntime chatRuntime;
    private final AiEnhanceResultMapper enhanceResultMapper;

    public EnhanceServiceImpl(ChatRuntime chatRuntime, AiEnhanceResultMapper enhanceResultMapper) {
        this.chatRuntime = chatRuntime;
        this.enhanceResultMapper = enhanceResultMapper;
    }

    @Override
    public EnhanceResultVO enhance(EnhanceRequestDTO dto) {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (StrUtil.isBlank(content)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "待处理内容不能为空");
        }
        AiScene scene = parseScene(dto.getScene());
        if (scene == AiScene.SUMMARY) {
            // SUMMARY 复用可复用的摘要生成方法（发布事件监听同款路径）
            return generateAndStoreSummary(workspaceId, dto.getKnowledgeId(), null, content);
        }
        String resultJson = generate(workspaceId, scene, content);
        return store(workspaceId, dto.getKnowledgeId(), scene, resultJson);
    }

    @Override
    public EnhanceResultVO generateAndStoreSummary(Long workspaceId, Long knowledgeId, String title, String content) {
        if (workspaceId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "工作空间不能为空");
        }
        // 标题拼入待摘要文本（可空），正文为空视为非法入参
        String text = StrUtil.isBlank(title) ? "" : title.trim() + "\n\n";
        text += content == null ? "" : content.trim();
        if (StrUtil.isBlank(text)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "待处理内容不能为空");
        }
        String resultJson = generate(workspaceId, AiScene.SUMMARY, text);
        return store(workspaceId, knowledgeId, AiScene.SUMMARY, resultJson);
    }

    /** 落库并组装返回视图（scene/knowledgeId 由调用方填充）。 */
    private EnhanceResultVO store(Long workspaceId, Long knowledgeId, AiScene scene, String resultJson) {
        AiEnhanceResultEntity entity = new AiEnhanceResultEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setKnowledgeId(knowledgeId);
        entity.setScene(scene.name());
        entity.setResultJson(resultJson);
        // DB 有 DEFAULT CURRENT_TIMESTAMP 但 MyBatis-Plus insert 不回填内存实体，需手动赋值
        entity.setCreatedAt(java.time.LocalDateTime.now());
        enhanceResultMapper.insert(entity);
        return EnhanceResultVO.builder()
                .id(entity.getId())
                .knowledgeId(entity.getKnowledgeId())
                .scene(entity.getScene())
                .resultJson(entity.getResultJson())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AiScene parseScene(String scene) {
        if ("SUMMARY".equalsIgnoreCase(scene)) {
            return AiScene.SUMMARY;
        }
        if ("SEO".equalsIgnoreCase(scene)) {
            return AiScene.SEO;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "场景仅支持 SUMMARY|SEO");
    }

    /** 调用运行时生成并校验结构化结果，返回紧凑 JSON 文本。 */
    private String generate(Long workspaceId, AiScene scene, String content) {
        String system = scene == AiScene.SUMMARY ? PromptTemplates.SUMMARY : PromptTemplates.SEO;
        String raw = chatRuntime.chat(workspaceId, scene,
                List.of(
                        new SystemMessage(system),
                        new UserMessage(content)),
                0.3, 1024);
        JSONObject obj = parseJson(raw);
        validate(scene, obj);
        return obj.toString();
    }

    private void validate(AiScene scene, JSONObject obj) {
        if (scene == AiScene.SUMMARY) {
            if (StrUtil.isBlank(obj.getStr("summary"))) {
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "AI 输出缺少摘要字段，请重试");
            }
        } else {
            if (StrUtil.isBlank(obj.getStr("title"))
                    || obj.get("keywords") == null
                    || StrUtil.isBlank(obj.getStr("description"))) {
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "AI 输出缺少 SEO 字段，请重试");
            }
        }
    }

    /** 提取 JSON 对象：解析失败视为 AI 输出异常（区别于写作/审校的容错语义）。 */
    private JSONObject parseJson(String raw) {
        JSONObject obj = AiJson.extractObject(raw);
        if (obj == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "AI 输出不是合法 JSON，请重试");
        }
        return obj;
    }
}
