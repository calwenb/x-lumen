package com.calwen.xlumen.ai.service.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskExecutor;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.service.tool.AgentToolContext;
import com.calwen.xlumen.ai.service.tool.ToolEventSink;
import com.calwen.xlumen.ai.service.tool.ToolRun;
import com.calwen.xlumen.ai.vo.ReviewIssueVO;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 审校执行器（F-0604）：非流式输出严格 JSON 数组，Hutool JSONUtil 校验字段；
 * 校验失败重试一次，仍失败则任务 FAILED。
 * IDEA-025 F-0604 事实核对模式（agent_enabled=1）：挂 knowledge.search 工具走 ChatRuntime 工具化非流式
 * （ChatClient 自动循环，独立轮数上限 reviewerAgentMaxRounds），输出 Schema 不变 + 可选证据字段；闸门语义不变——
 * 工具检索失败 ≠ 任务失败（错误信封给模型，模型继续文本层检查），仅任务本身失败才阻断发布（F-0907）。
 * OPT-2/D20 全量迁移：链路改走 ChatRuntime（Spring AI 消息类型），业务语义不变。
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

    /** 事实核对模式 System 提示词：追加工具检索核对与可选证据字段。 */
    private static final String SYSTEM_PROMPT_AGENT = "你是严格的审校助手。请审校用户提供的文章："
            + "可用 knowledge.search 工具检索库内已有知识，核对文中关键论断是否与库内知识矛盾或术语不一致；"
            + "核对后输出一个严格的 JSON 数组，每个元素包含四个字段："
            + "severity（取值为 error|warning|info）、position（原文位置引用）、evidence（证据）、suggestion（修改建议），"
            + "矛盾类问题可附可选字段 evidenceKnowledgeId（库内证据知识 ID）与 evidenceQuote（库内证据原文引用）。"
            + "工具检索失败不影响审校：基于已有文本层检查继续输出。只输出 JSON 数组，不要输出任何其他内容。";

    /** 重试追加提示。 */
    private static final String RETRY_HINT = "\n\n请重新输出，必须是 JSON 数组，每个元素含 severity/position/evidence/suggestion 四个字段。";

    private final ChatRuntime chatRuntime;

    public ReviewExecutor(ChatRuntime chatRuntime) {
        this.chatRuntime = chatRuntime;
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
        boolean agentMode = Boolean.TRUE.equals(
                chatRuntime.resolveScene(task.getWorkspaceId(), AiScene.REVIEWER).getAgentEnabled());
        String raw;
        if (agentMode) {
            raw = chatWithTools(task, userPrompt);
        } else {
            raw = chat(task, userPrompt);
        }
        List<ReviewIssueVO> issues = parseIssues(raw);
        if (issues == null) {
            raw = agentMode ? chatWithTools(task, userPrompt + RETRY_HINT) : chat(task, userPrompt + RETRY_HINT);
            issues = parseIssues(raw);
        }
        if (issues == null) {
            ctx.fail("审校输出必须是 JSON 数组");
            return;
        }
        ctx.publishProgress(90);
        ctx.complete(JSONUtil.toJsonStr(issues));
    }

    /** 普通模式：单次非流式 chat。 */
    private String chat(AiTaskEntity task, String userPrompt) {
        return chatRuntime.chat(task.getWorkspaceId(), AiScene.REVIEWER,
                List.of(
                        new SystemMessage(SYSTEM_PROMPT),
                        new UserMessage(userPrompt)),
                0.2, 2048);
    }

    /** 事实核对模式：ChatRuntime 非流式工具循环（轮数上限在 ToolCallbackAdapter 内收紧）。 */
    private String chatWithTools(AiTaskEntity task, String userPrompt) {
        List<Message> messages = List.of(
                new SystemMessage(SYSTEM_PROMPT_AGENT),
                new UserMessage(userPrompt));
        AgentToolContext toolContext = AgentToolContext.builder()
                .workspaceId(task.getWorkspaceId())
                .userId(task.getUserId())
                .build();
        ToolRun run = ToolRun.builder()
                .scene(AiScene.REVIEWER)
                .toolContext(toolContext)
                .sink(new ToolEventSink())
                .build();
        try {
            return chatRuntime.chatWithTools(task.getWorkspaceId(), AiScene.REVIEWER,
                    messages, 0.2, 2048, run);
        } catch (Exception e) {
            throw new IllegalStateException("AI 服务不可用", e);
        }
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

    /** 解析 JSON 数组并校验字段（可选证据字段不强制），缺失必填字段返回 null。 */
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
                        .evidenceKnowledgeId(item.getStr("evidenceKnowledgeId"))
                        .evidenceQuote(item.getStr("evidenceQuote"))
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