package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.dto.AssistDTO;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AssistService;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.List;

/**
 * AI 交互式辅助实现：按能力装配指令提示词，走 ChatRuntime 非流式（场景 WRITING，纳入配额与调用追踪）。
 * 图片讲解走专用视觉模型（chatWithModel + AiProperties 视觉模型默认 qwen3-vl-flash），其余走场景解析模型。
 * 提示词为交互式固定指令（非写作多槽位主链路，不做后台配置）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class AssistServiceImpl implements AssistService {

    private static final double TEMPERATURE = 0.5;

    private static final int MAX_TOKENS = 2048;

    private final ChatRuntime chatRuntime;
    private final AiProperties aiProperties;

    public AssistServiceImpl(ChatRuntime chatRuntime, AiProperties aiProperties) {
        this.chatRuntime = chatRuntime;
        this.aiProperties = aiProperties;
    }

    @Override
    public String assist(Long workspaceId, AssistDTO dto) {
        String action = dto.getAction().trim().toLowerCase();
        String system = systemFor(action);
        if ("image_explain".equals(action)) {
            return chatRuntime.chatWithModel(workspaceId, AiScene.WRITING, aiProperties.getBailianModelVision(),
                    List.of(new SystemMessage(system), imageMessage(dto)), TEMPERATURE, MAX_TOKENS).trim();
        }
        Message user = new UserMessage(buildUserText(dto));
        return chatRuntime.chat(workspaceId, AiScene.WRITING,
                List.of(new SystemMessage(system), user), TEMPERATURE, MAX_TOKENS).trim();
    }

    /** 图片讲解消息：附媒体内容（模型需具备视觉能力，如 qwen-vl；文本模型会忽略图片按提示词作答）。 */
    private Message imageMessage(AssistDTO dto) {
        String url = StrUtil.blankToDefault(dto.getImageUrl(), "").trim();
        if (StrUtil.isBlank(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BizException(ErrorCode.INVALID_PARAM, "图片地址无效，需为 http(s) 公网可访问链接");
        }
        return UserMessage.builder()
                .text("请讲解这张图片：" + url + (StrUtil.isNotBlank(dto.getSelection()) ? "\n补充说明：" + dto.getSelection() : ""))
                .media(new Media(MimeTypeUtils.parseMimeType("image/jpeg"), URI.create(url)))
                .build();
    }

    private String systemFor(String action) {
        return switch (action) {
            case "continue" -> "你是小光，一名中文内容创作助手。请基于给定文本的结尾自然续写 200~500 字，"
                    + "保持原有语气、结构与 Markdown 风格，只输出续写内容，不要重复已有内容。";
            case "polish" -> "你是小光，一名中文编辑。请对给定文本润色：优化通顺度与表达专业性，"
                    + "不改变核心内容与结构，直接输出润色后的完整文本。";
            case "titles" -> "你是小光，一名标题策划。请为给定文本生成 5 个备选标题，每行一个、不带编号，"
                    + "贴合主题与受众，直接输出标题列表。";
            case "spellfix" -> "你是小光，一名文字校对。请修正给定文本中的错别字、误用标点与明显语病，"
                    + "保持内容与结构不变，直接输出修正后的完整文本。";
            case "code_explain" -> "你是小光，一名资深工程师。请解释给定代码的作用、关键逻辑与潜在问题，"
                    + "用 Markdown 输出：先一句总述，再分要点说明。";
            case "code_bug" -> "你是小光，一名代码评审专家。请分析给定代码的缺陷与 bug，"
                    + "用 Markdown 分条列出问题点与修复建议（含关键代码示意）。";
            case "code_test" -> "你是小光，一名测试工程师。请为给定代码生成可运行的单元测试，"
                    + "包含必要的 import、测试用例与断言，用与代码一致的编程语言代码块输出。";
            case "kb_insight" -> "你是小光，一名知识库运营专家。请根据给定的知识文档列表，"
                    + "输出知识库主题概览与新增内容要点，用 Markdown 紧凑输出：顶部一句话总结，"
                    + "再分主题列出要点（每点一行），最后给出新增或亮点内容小结，不要输出与列表无关的内容。";
            case "kb_cluster" -> "你是小光，一名知识聚类分析专家。给定输入为「编号. 标题——摘要」列表行，"
                    + "请把主题相近的内容聚为一类，输出一个严格的 JSON 数组，每项为 {\"topic\":\"主题名\",\"ids\":[编号...]}，"
                    + "只输出 JSON 数组本身，不要包含任何解释或 Markdown 围栏。";
            case "image_explain" -> "你是小光，一名技术图解助手。请讲解图片内容：先一句话概述，"
                    + "再用 Markdown 分要点说明图片中的结构、关系或关键信息；若无法看到图片请明确说明。";
            default -> throw new IllegalArgumentException("未知辅助能力：" + action);
        };
    }

    private String buildUserText(AssistDTO dto) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(dto.getTitle())) {
            sb.append("标题/主题：").append(dto.getTitle()).append('\n');
        }
        sb.append("正文内容：\n").append(dto.getContent());
        if (StrUtil.isNotBlank(dto.getSelection())) {
            sb.append("\n\n选中部分（若适用优先处理此段）：\n").append(dto.getSelection());
        }
        return sb.toString();
    }
}