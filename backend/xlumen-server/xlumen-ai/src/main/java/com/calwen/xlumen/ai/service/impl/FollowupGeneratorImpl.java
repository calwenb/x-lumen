package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.FollowupGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 相关追问生成实现：轻量调用（WRITING 场景，低温度收紧输出），按行解析取前 3 条非空；
 * 生成本身失败静默返回空列表，不阻断对话主流程。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class FollowupGeneratorImpl implements FollowupGenerator {

    private static final Logger log = LoggerFactory.getLogger(FollowupGeneratorImpl.class);

    /** 追问条数上限。 */
    private static final int MAX_FOLLOWUPS = 3;

    /** 追问生成系统提示。 */
    public static final String SYSTEM_PROMPT = "你是小光，一名知识问答助手。"
            + "请基于用户问题与回答生成 3 个相关追问，每行一个，不带编号，控制在 20 字内。";

    private final ChatRuntime chatRuntime;

    public FollowupGeneratorImpl(ChatRuntime chatRuntime) {
        this.chatRuntime = chatRuntime;
    }

    @Override
    public List<String> generate(Long workspaceId, String question, String answerSummary) {
        String user = "问题：" + (question == null ? "" : question)
                + "\n回答（摘要）：" + (answerSummary == null ? "" : answerSummary);
        try {
            String raw = chatRuntime.chat(workspaceId, AiScene.WRITING,
                    List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(user)), 0.5, 256);
            return parse(raw);
        } catch (Exception e) {
            log.warn("相关追问生成失败（静默跳过）ws={}", workspaceId, e);
            return List.of();
        }
    }

    @Override
    public List<String> parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String text = line == null ? "" : line.trim();
            if (StrUtil.isBlank(text)) {
                continue;
            }
            out.add(text.length() > 40 ? text.substring(0, 40) : text);
            if (out.size() >= MAX_FOLLOWUPS) {
                break;
            }
        }
        return out;
    }
}