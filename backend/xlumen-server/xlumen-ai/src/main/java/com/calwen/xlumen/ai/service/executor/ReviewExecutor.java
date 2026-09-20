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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 审校执行器（双轨合并后单轨）：统一走事实核对模式 —— 挂 knowledge.search 工具走 ChatRuntime 工具化非流式
 * （ChatClient 自动循环，轮数上限 reviewerAgentMaxRounds），输出严格 JSON 数组，Hutool JSONUtil 校验字段，失败重试一次。
 * 长文按段落边界分片审校：单次 token 预算不足以输出全部意见时会被截断，分片可保证任意长度长文都能得到结论；
 * 分片结果合并去重（position+evidence）。
 * 闸门语义不变：工具检索失败 ≠ 任务失败（错误信封给模型，模型继续文本层检查），分片全部失败才阻断发布。
 * D20 全量迁移：链路改走 ChatRuntime（Spring AI 消息类型），业务语义不变。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class ReviewExecutor implements AiTaskExecutor {

    /** 单次审校输出上限：意见数组较长，2048 易被截断。 */
    private static final int REVIEW_MAX_TOKENS = 4096;
    /** 单次送审内容上限（字符），超出按段落边界分片。 */
    private static final int REVIEW_SHARD_CHARS = 6000;

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
        List<String> shards = splitContent(content);
        List<ReviewIssueVO> merged = new ArrayList<>();
        boolean anySuccess = false;
        for (String shard : shards) {
            List<ReviewIssueVO> issues = reviewShard(task, buildUserPrompt(title, shard));
            if (issues == null) {
                // 单片拿不到结论（截断/非法输出）不影响其它分片；仅全部失败才判任务失败
                continue;
            }
            anySuccess = true;
            merged.addAll(issues);
        }
        if (!anySuccess) {
            ctx.fail("审校输出必须是 JSON 数组");
            return;
        }
        ctx.publishProgress(90);
        ctx.complete(JSONUtil.toJsonStr(dedup(merged)));
    }

    /** 单片审校：解析失败追加提示重试一次；仍失败返回 null。 */
    private List<ReviewIssueVO> reviewShard(AiTaskEntity task, String userPrompt) {
        String raw = chatWithTools(task, userPrompt);
        List<ReviewIssueVO> issues = parseIssues(raw);
        if (issues == null) {
            raw = chatWithTools(task, userPrompt + PromptTemplates.REVIEWER_RETRY_HINT);
            issues = parseIssues(raw);
        }
        return issues;
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
                    messages, 0.2, REVIEW_MAX_TOKENS, run);
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

    /**
     * 长文分片：不超过阈值整段返回；超出则按行（段落）边界累积切分，尽量不切断 Markdown 结构，
     * 并给每片补上「所在章节」上下文（该片之前的最近标题行）。
     */
    private List<String> splitContent(String content) {
        String text = content == null ? "" : content;
        if (text.length() <= REVIEW_SHARD_CHARS) {
            return List.of(text);
        }
        List<String> shards = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        StringBuilder buf = new StringBuilder();
        String lastHeading = null;
        String contextHeading = null;
        for (String line : lines) {
            if (buf.length() > 0 && buf.length() + line.length() + 1 > REVIEW_SHARD_CHARS) {
                shards.add(renderShard(contextHeading, buf.toString()));
                buf.setLength(0);
                contextHeading = lastHeading;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                lastHeading = trimmed;
            }
            buf.append(line).append('\n');
        }
        if (buf.length() > 0) {
            shards.add(renderShard(contextHeading, buf.toString()));
        }
        return shards;
    }

    /** 分片标题上下文前缀（无前置标题时原样返回）。 */
    private String renderShard(String contextHeading, String text) {
        if (StrUtil.isBlank(contextHeading)) {
            return text;
        }
        return "所在章节：" + contextHeading + "\n" + text;
    }

    private String buildUserPrompt(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            sb.append("文章标题：").append(title).append('\n');
        }
        sb.append("待审校正文：\n").append(content);
        return sb.toString();
    }

    /**
     * 宽松解析 JSON 数组并逐元素校验：整段被截断时尽量抢救完整对象；缺少必填字段的单个元素跳过而非整批失败。
     * 数组整体不可解析、或数组非空但无一个合法元素（不能当作「无问题」结论）时返回 null。
     */
    private List<ReviewIssueVO> parseIssues(String raw) {
        JSONArray arr = AiJson.extractArrayLenient(raw);
        if (arr == null) {
            return null;
        }
        List<ReviewIssueVO> issues = new ArrayList<>();
        for (Object o : arr) {
            if (!(o instanceof JSONObject item)) {
                continue;
            }
            String severity = item.getStr("severity");
            String position = item.getStr("position");
            String evidence = item.getStr("evidence");
            String suggestion = item.getStr("suggestion");
            if (StrUtil.isBlank(severity) || StrUtil.isBlank(position)
                    || StrUtil.isBlank(evidence) || StrUtil.isBlank(suggestion)) {
                continue;
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
        if (!arr.isEmpty() && issues.isEmpty()) {
            return null;
        }
        return issues;
    }

    /** 合并分片结果去重：相同 position+evidence 视为同一问题（分片边界可能重复报告）。 */
    private List<ReviewIssueVO> dedup(List<ReviewIssueVO> issues) {
        Map<String, ReviewIssueVO> unique = new LinkedHashMap<>();
        for (ReviewIssueVO issue : issues) {
            unique.putIfAbsent(issue.getPosition() + "\u0000" + issue.getEvidence(), issue);
        }
        return new ArrayList<>(unique.values());
    }
}