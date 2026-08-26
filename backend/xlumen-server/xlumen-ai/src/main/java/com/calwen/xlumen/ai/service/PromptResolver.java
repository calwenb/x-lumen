package com.calwen.xlumen.ai.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.prompt.PromptTemplates;
import org.springframework.stereotype.Service;

/**
 * 场景提示词解析：ai_scene_config.prompt 优先（WRITING 为 JSON 多槽位），
 * 缺省回退 PromptTemplates 常量默认（Prompt 后台动态管理的默认值落点）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class PromptResolver {

    private final SceneConfigService sceneConfigService;

    public PromptResolver(SceneConfigService sceneConfigService) {
        this.sceneConfigService = sceneConfigService;
    }

    /** 单槽场景提示词：REVIEWER/QA/SUMMARY/SEO（WRITING 请走 {@link #resolveWriting}）。 */
    public String resolve(Long workspaceId, AiScene scene) {
        String configured = sceneConfigService.resolve(workspaceId, scene).getPrompt();
        if (StrUtil.isNotBlank(configured)) {
            return configured;
        }
        return switch (scene) {
            case REVIEWER -> PromptTemplates.REVIEWER_SYSTEM;
            case QA -> PromptTemplates.QA_AGENT;
            case SUMMARY -> PromptTemplates.SUMMARY;
            case SEO -> PromptTemplates.SEO;
            case WRITING -> PromptTemplates.WRITING_OUTLINE;
        };
    }

    /** 写作槽位提示词：outline/chapter/self_review/revise；配置为 JSON 且含槽位时优先，否则回退常量。 */
    public String resolveWriting(Long workspaceId, String slot) {
        String configured = sceneConfigService.resolve(workspaceId, AiScene.WRITING).getPrompt();
        if (StrUtil.isNotBlank(configured)) {
            try {
                JSONObject obj = JSONUtil.parseObj(configured);
                String slotPrompt = obj.getStr(slot);
                if (StrUtil.isNotBlank(slotPrompt)) {
                    return slotPrompt;
                }
            } catch (Exception ignored) {
                // 非 JSON 配置视为整体覆盖无效，走默认
            }
        }
        return switch (slot) {
            case "outline" -> PromptTemplates.WRITING_OUTLINE;
            case "chapter" -> PromptTemplates.WRITING_CHAPTER;
            case "self_review" -> PromptTemplates.WRITING_SELF_REVIEW;
            default -> PromptTemplates.WRITING_REVISE;
        };
    }
}