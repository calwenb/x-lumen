package com.calwen.xlumen.ai.service.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskExecutor;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import com.calwen.xlumen.ai.vo.ReviewIssueVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 审校执行器（F-0604）：非流式输出严格 JSON 数组，Hutool JSONUtil 校验字段；
 * 校验失败重试一次，仍失败则任务 FAILED。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class ReviewExecutor implements AiTaskExecutor {

    /** 审校 System 提示词：要求输出严格 JSON 数组。 */
    private static final String SYSTEM_PROMPT = "你是严格的审校助手。请审校用户提供的文章，"
            + "输出一个严格的 JSON 数组，每个元素包含四个字段："
            + "severity（取值为 error|warning|info）、position（原文位置引用）、evidence（证据）、suggestion（修改建议）。"
            + "只输出 JSON 数组，不要输出任何其他内容。";

    /** 重试追加提示。 */
    private static final String RETRY_HINT = "\n\n请重新输出，必须是 JSON 数组，每个元素含 severity/position/evidence/suggestion 四个字段。";

    private final ModelGateway modelGateway;

    public ReviewExecutor(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public AiScene scene() {
        return AiScene.REVIEWER;
    }

    @Override
    public void execute(AiTaskEntity task, TaskContext ctx) {
        JSONObject input = parseInput(task.getInputJson());
        String title = input.getStr("title");
        String content = input.getStr("content");
        if (StrUtil.isBlank(content)) {
            ctx.fail("待审校内容为空");
            return;
        }
        ctx.publishProgress(20);
        String userPrompt = buildUserPrompt(title, content);
        String raw = chat(task, userPrompt);
        List<ReviewIssueVO> issues = parseIssues(raw);
        if (issues == null) {
            raw = chat(task, userPrompt + RETRY_HINT);
            issues = parseIssues(raw);
        }
        if (issues == null) {
            ctx.fail("审校输出必须是 JSON 数组");
            return;
        }
        ctx.publishProgress(90);
        ctx.complete(JSONUtil.toJsonStr(issues));
    }

    private String chat(AiTaskEntity task, String userPrompt) {
        ProviderChatRequest request = ProviderChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                        ChatMessage.builder().role("user").content(userPrompt).build()))
                .temperature(0.2)
                .maxTokens(2048)
                .stream(false)
                .build();
        return modelGateway.chat(task.getWorkspaceId(), AiScene.REVIEWER, request);
    }

    private JSONObject parseInput(String inputJson) {
        if (StrUtil.isBlank(inputJson)) {
            return JSONUtil.createObj();
        }
        try {
            return JSONUtil.parseObj(inputJson);
        } catch (Exception e) {
            return JSONUtil.createObj();
        }
    }

    private String buildUserPrompt(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            sb.append("文章标题：").append(title).append('\n');
        }
        sb.append("待审校正文：\n").append(content);
        return sb.toString();
    }

    /** 解析 JSON 数组并校验字段，缺失字段返回 null。 */
    private List<ReviewIssueVO> parseIssues(String raw) {
        String json = extractJsonArray(raw);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(json);
            List<ReviewIssueVO> issues = new ArrayList<>();
            for (Object o : arr) {
                JSONObject item = (JSONObject) o;
                String severity = item.getStr("severity");
                String position = item.getStr("position");
                String evidence = item.getStr("evidence");
                String suggestion = item.getStr("suggestion");
                if (StrUtil.isBlank(severity) || StrUtil.isBlank(position)
                        || StrUtil.isBlank(evidence) || StrUtil.isBlank(suggestion)) {
                    return null;
                }
                issues.add(ReviewIssueVO.builder()
                        .severity(severity)
                        .position(position)
                        .evidence(evidence)
                        .suggestion(suggestion)
                        .build());
            }
            return issues;
        } catch (Exception e) {
            return null;
        }
    }

    /** 提取 JSON 数组：去除代码围栏并截取首尾方括号。 */
    private String extractJsonArray(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            s = firstNewline >= 0 ? s.substring(firstNewline + 1) : s;
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }
}
