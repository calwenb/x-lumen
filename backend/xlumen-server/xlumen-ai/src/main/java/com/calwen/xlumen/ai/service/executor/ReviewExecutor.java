package com.calwen.xlumen.ai.service.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.prompt.PromptTemplates;
import com.calwen.xlumen.ai.service.AiTaskExecutor;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.PromptResolver;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.service.tool.AgentToolContext;
import com.calwen.xlumen.ai.service.tool.ToolEventSink;
import com.calwen.xlumen.ai.service.tool.ToolRun;
import com.calwen.xlumen.ai.util.AiJson;
import com.calwen.xlumen.ai.vo.ReviewIssueVO;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 审校执行器（双轨合并后单轨）：统一走事实核对模式 —— 挂 knowledge.search 工具走 ChatRuntime 工具化非流式
 * （ChatClient 自动循环，轮数上限 reviewerAgentMaxRounds），输出严格 JSON 数组，Hutool JSONUtil 校验字段，失败重试一次。
 * 闸门语义不变：工具检索失败 ≠ 任务失败（错误信封给模型，模型继续文本层检查），仅任务本身失败才阻断发布。
 * D20 全量迁移：链路改走 ChatRuntime（Spring AI 消息类型），业务语义不变。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class ReviewExecutor implements AiTaskExecutor {

    private final ChatRuntime chatRuntime;
    private final PromptResolver promptResolver;

    public ReviewExecutor(ChatRuntime chatRuntime, PromptResolver promptResolver) {
        this.chatRuntime = chatRuntime;
        this.promptResolver = promptResolver;
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
        // 双轨合并：审校统一走事实核对模式（挂 knowledge.search 工具），不再保留普通文本审校路径
        String raw = chatWithTools(task, userPrompt);
        List<ReviewIssueVO> issues = parseIssues(raw);
        if (issues == null) {
            raw = chatWithTools(task, userPrompt + PromptTemplates.REVIEWER_RETRY_HINT);
            issues = parseIssues(raw);
        }
        if (issues == null) {
            ctx.fail("审校输出必须是 JSON 数组");
            return;
        }
        ctx.publishProgress(90);
        ctx.complete(JSONUtil.toJsonStr(issues));
    }

    /** 事实核对模式：ChatRuntime 非流式工具循环（轮数上限在 ToolCallbackAdapter 内收紧）。 */
    private String chatWithTools(AiTaskEntity task, String userPrompt) {
        List<Message> messages = List.of(
                new SystemMessage(promptResolver.resolve(task.getWorkspaceId(), AiScene.REVIEWER)),
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
        JSONArray arr = AiJson.extractArray(raw);
        if (arr == null) {
            return null;
        }
        try {
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
}