package com.calwen.xlumen.ai.service.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskExecutor;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import com.calwen.xlumen.ai.service.provider.ProviderChatResult;
import com.calwen.xlumen.ai.service.provider.StreamCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 写作执行器（F-0601）：单次流式调用网关，chunk 推 SSE，完成后解析标题与正文写入 resultJson。
 * IDEA-025 F-0608 多步工作流（agent_enabled=1）：大纲 → 分章流式 → 异源自审（REVIEWER）→ 修订，
 * 全程无人工确认断点；四条降级路径全部回退单次生成（大纲解析失败/章节超限/单章失败/自审失败跳过修订）——
 * 写作功能永不因新模式不可用。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class WritingExecutor implements AiTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(WritingExecutor.class);

    /** 写作 System 提示词：明确角色与输出格式。 */
    private static final String SYSTEM_PROMPT = "你是小光，一名专业的中文内容创作助手。"
            + "请根据用户提供的主题、草稿或素材，输出一篇结构完整、带标题的完整 Markdown 文章。"
            + "第一行必须是 # 标题，其余为正文内容。";

    /** 大纲 System 提示词：输出章节标题+要点 JSON。 */
    private static final String OUTLINE_PROMPT = "你是小光，一名专业的中文内容创作助手。"
            + "请为给定主题/草稿规划文章大纲，只输出一个 JSON 对象，格式为："
            + "{\"chapters\":[{\"title\":\"章节标题\",\"points\":[\"要点1\",\"要点2\"]}]}，"
            + "章节数量不超过 {{MAX}} 章，不要输出任何其他内容。";

    /** 分章 System 提示词：只输出指定章节内容。 */
    private static final String CHAPTER_PROMPT = "你是小光，一名专业的中文内容创作助手。"
            + "你正在撰写一篇结构化长文，请只输出「{{TITLE}}」这一章的 Markdown 正文；"
            + "不要输出章节标题行，不要重复整篇文章标题，不要输出文章结尾总结。";

    /** 自审 System 提示词：与审校模型异源（F-0604）。 */
    private static final String SELF_REVIEW_PROMPT = "你是严格的审校助手（与写作模型异源）。"
            + "请审校下列长文，输出一个严格的 JSON 数组，每个元素含四个字段："
            + "severity（error|warning|info）、position（位置）、evidence（证据）、suggestion（修改建议）。"
            + "只输出 JSON 数组，不要输出其他内容。";

    /** 修订 System 提示词：按意见修订全文。 */
    private static final String REVISE_PROMPT = "你是小光，一名专业的中文内容创作助手。"
            + "下面是全文初稿与审校意见，请根据意见修订全文，输出修订后的完整 Markdown 文章，"
            + "第一行必须是 # 标题。不要输出其他内容。";

    private final ModelGateway modelGateway;
    private final com.calwen.xlumen.ai.config.AiProperties aiProperties;

    public WritingExecutor(ModelGateway modelGateway, com.calwen.xlumen.ai.config.AiProperties aiProperties) {
        this.modelGateway = modelGateway;
        this.aiProperties = aiProperties;
    }

    @Override
    public AiScene scene() {
        return AiScene.WRITING;
    }

    @Override
    public void execute(AiTaskEntity task, TaskContext ctx) {
        JSONObject input = parseInput(task.getInputJson());
        boolean agentMode = Boolean.TRUE.equals(
                modelGateway.resolveScene(task.getWorkspaceId(), AiScene.WRITING).getAgentEnabled());
        if (agentMode) {
            executeAgentWorkflow(task, ctx, input);
        } else {
            singlePass(task, ctx, input);
        }
    }

    /** 现状单次流式生成（兜底路径，agent 模式任何步骤失败均回退此处）。 */
    private void singlePass(AiTaskEntity task, TaskContext ctx, JSONObject input) {
        String topic = input.getStr("topic");
        String draft = input.getStr("draft");
        String content = input.getStr("content");
        String title = input.getStr("title");

        ctx.publishProgress(10);
        StringBuilder sb = new StringBuilder();
        AtomicReference<String> error = new AtomicReference<>();
        ProviderChatRequest request = ProviderChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                        ChatMessage.builder().role("user").content(buildUserPrompt(topic, draft, content, title)).build()))
                .temperature(0.7)
                .maxTokens(2048)
                .stream(true)
                .build();
        modelGateway.chatStream(task.getWorkspaceId(), AiScene.WRITING, request,
                new StreamCallback() {
                    @Override
                    public void onContent(String delta) {
                        sb.append(delta);
                        ctx.publishChunk(delta);
                    }

                    @Override
                    public void onResult(ProviderChatResult result) {
                        // 单次生成模式终态无额外处理
                    }
                },
                err -> error.set(err == null ? "AI 服务不可用" : err.getMessage()));
        if (error.get() != null) {
            ctx.fail(error.get());
            return;
        }
        ctx.publishProgress(90);
        String[] parts = splitTitle(sb.toString(), title);
        String resultJson = JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("title", parts[0])
                .set("content", parts[1]));
        ctx.complete(resultJson);
    }

    /** 多步工作流：大纲 → 分章 → 自审 → 修订（四条降级路径回退单次生成）。 */
    private void executeAgentWorkflow(AiTaskEntity task, TaskContext ctx, JSONObject input) {
        String topic = input.getStr("topic");
        String draft = input.getStr("draft");
        String content = input.getStr("content");
        String title = input.getStr("title");

        // 步1 大纲（非流式 chat 输出 JSON）
        ctx.publishProgress(10);
        List<JSONObject> chapters = outline(task, input);
        if (chapters == null || chapters.isEmpty()) {
            log.info("写作多步：大纲解析失败，回退单次生成 taskId={}", task.getId());
            singlePass(task, ctx, input);
            return;
        }
        ctx.publishProgress(20);

        // 步2 分章生成（逐章流式）
        StringBuilder full = new StringBuilder();
        StringBuilder outlineText = new StringBuilder();
        for (JSONObject ch : chapters) {
            if (outlineText.length() > 0) {
                outlineText.append("；");
            }
            outlineText.append(ch.getStr("title", ""));
        }
        String userContext = buildUserPrompt(topic, draft, content, title);
        for (int i = 0; i < chapters.size(); i++) {
            JSONObject ch = chapters.get(i);
            String chapterTitle = ch.getStr("title", "第 " + (i + 1) + " 章");
            ctx.publishChunk("\n\n--- 第 " + (i + 1) + "/" + chapters.size() + " 章：" + chapterTitle + " ---\n");
            String chapterText = generateChapter(task, ctx, userContext, outlineText.toString(), chapterTitle);
            if (chapterText == null) {
                log.info("写作多步：第 {} 章生成失败，回退单次生成 taskId={}", i + 1, task.getId());
                singlePass(task, ctx, input);
                return;
            }
            full.append(chapterText).append("\n\n");
            ctx.publishProgress(20 + 60 * (i + 1) / chapters.size());
        }
        String fullText = full.toString().trim();

        // 步3 自审（REVIEWER 异源非流式）
        ctx.publishProgress(85);
        String reviewJson = selfReview(task, fullText);
        String revised = fullText;
        if (reviewJson != null) {
            // 步4 修订一轮
            ctx.publishProgress(90);
            revised = revise(task, ctx, fullText, reviewJson);
            if (StrUtil.isBlank(revised)) {
                log.info("写作多步：修订失败，交付初稿 taskId={}", task.getId());
                revised = fullText;
            }
        } else {
            log.info("写作多步：自审失败，跳过修订直接交付 taskId={}", task.getId());
        }

        String[] parts = splitTitle(revised, title);
        String resultJson = JSONUtil.toJsonStr(JSONUtil.createObj()
                .set("title", parts[0])
                .set("content", parts[1])
                .set("outline", JSONUtil.toJsonStr(JSONUtil.createObj().set("chapters", chapters)))
                .set("reviewIssues", reviewJson == null ? "[]" : reviewJson));
        ctx.complete(resultJson);
    }

    /** 步1：大纲；解析失败或章节超限返回 null（回退单次）。 */
    private List<JSONObject> outline(AiTaskEntity task, JSONObject input) {
        String prompt = OUTLINE_PROMPT.replace("{{MAX}}", String.valueOf(maxChapters()));
        ProviderChatRequest request = ProviderChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(prompt).build(),
                        ChatMessage.builder().role("user").content(
                                buildUserPrompt(input.getStr("topic"), input.getStr("draft"),
                                        input.getStr("content"), input.getStr("title"))).build()))
                .temperature(0.4)
                .maxTokens(1024)
                .stream(false)
                .build();
        try {
            ProviderChatResult result = modelGateway.chat(task.getWorkspaceId(), AiScene.WRITING, request);
            JSONObject obj = extractJsonObject(result.getContent());
            if (obj == null) {
                return null;
            }
            JSONArray chapters = obj.getJSONArray("chapters");
            if (chapters == null || chapters.isEmpty() || chapters.size() > maxChapters()) {
                return null;
            }
            List<JSONObject> list = new ArrayList<>();
            for (Object o : chapters) {
                if (o instanceof JSONObject ch && StrUtil.isNotBlank(ch.getStr("title"))) {
                    list.add(ch);
                }
            }
            return list.isEmpty() ? null : list;
        } catch (Exception e) {
            log.warn("写作多步：大纲生成失败", e);
            return null;
        }
    }

    /** 步2：单章流式生成；失败重试 1 次，仍失败返回 null（触发降级）。 */
    private String generateChapter(AiTaskEntity task, TaskContext ctx, String userContext,
                                   String outlineText, String chapterTitle) {
        String chapterPrompt = CHAPTER_PROMPT.replace("{{TITLE}}", chapterTitle);
        StringBuilder sb = new StringBuilder();
        for (int attempt = 0; attempt < 2; attempt++) {
            StringBuilder content = new StringBuilder();
            AtomicReference<String> error = new AtomicReference<>();
            String user = "全文大纲：" + outlineText + (StrUtil.isNotBlank(userContext) ? "\n" + userContext : "")
                    + "\n当前章节：" + chapterTitle;
            ProviderChatRequest request = ProviderChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(chapterPrompt).build(),
                            ChatMessage.builder().role("user").content(user).build()))
                    .temperature(0.7)
                    .maxTokens(2048)
                    .stream(true)
                    .build();
            modelGateway.chatStream(task.getWorkspaceId(), AiScene.WRITING, request,
                    new StreamCallback() {
                        @Override
                        public void onContent(String delta) {
                            content.append(delta);
                            ctx.publishChunk(delta);
                        }

                        @Override
                        public void onResult(ProviderChatResult result) {
                            // 章节流式终态无额外处理
                        }
                    },
                    err -> error.set(err == null ? "AI 服务不可用" : err.getMessage()));
            if (error.get() == null && content.length() > 0) {
                sb.append(content);
                return sb.toString();
            }
        }
        return null;
    }

    /** 步3：自审（REVIEWER 异源）；失败/解析失败返回 null（跳过修订）。 */
    private String selfReview(AiTaskEntity task, String fullText) {
        ProviderChatRequest request = ProviderChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(SELF_REVIEW_PROMPT).build(),
                        ChatMessage.builder().role("user").content(fullText).build()))
                .temperature(0.2)
                .maxTokens(2048)
                .stream(false)
                .build();
        try {
            ProviderChatResult result = modelGateway.chat(task.getWorkspaceId(), AiScene.REVIEWER, request);
            String json = extractJsonArray(result.getContent());
            if (StrUtil.isBlank(json)) {
                return null;
            }
            JSONArray arr = JSONUtil.parseArray(json);
            return arr.isEmpty() ? "[]" : arr.toString();
        } catch (Exception e) {
            log.warn("写作多步：自审失败，跳过修订", e);
            return null;
        }
    }

    /** 步4：按意见修订一轮；失败返回 null（交付初稿）。 */
    private String revise(AiTaskEntity task, TaskContext ctx, String fullText, String reviewJson) {
        StringBuilder sb = new StringBuilder();
        AtomicReference<String> error = new AtomicReference<>();
        ProviderChatRequest request = ProviderChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(REVISE_PROMPT).build(),
                        ChatMessage.builder().role("user").content("审校意见：\n" + reviewJson + "\n\n全文初稿：\n" + fullText).build()))
                .temperature(0.5)
                .maxTokens(4096)
                .stream(true)
                .build();
        modelGateway.chatStream(task.getWorkspaceId(), AiScene.WRITING, request,
                new StreamCallback() {
                    @Override
                    public void onContent(String delta) {
                        sb.append(delta);
                        ctx.publishChunk(delta);
                    }

                    @Override
                    public void onResult(ProviderChatResult result) {
                        // 修订流式终态无额外处理
                    }
                },
                err -> error.set(err == null ? "AI 服务不可用" : err.getMessage()));
        if (error.get() != null) {
            log.warn("写作多步：修订请求失败", error.get());
            return null;
        }
        return sb.toString();
    }

    private int maxChapters() {
        return aiProperties.getWritingMaxChapters();
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

    private String buildUserPrompt(String topic, String draft, String content, String title) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(topic)) {
            sb.append("主题：").append(topic).append('\n');
        }
        if (StrUtil.isNotBlank(title)) {
            sb.append("标题提示：").append(title).append('\n');
        }
        if (StrUtil.isNotBlank(draft)) {
            sb.append("草稿：\n").append(draft).append('\n');
        }
        if (StrUtil.isNotBlank(content)) {
            sb.append("素材/完整文章：\n").append(content).append('\n');
        }
        if (sb.length() == 0) {
            sb.append("请创作一篇内容。");
        }
        return sb.toString();
    }

    /** 解析首行 # 标题，其余为正文；无标题行时回退输入标题或默认标题。 */
    private String[] splitTitle(String full, String fallbackTitle) {
        String text = full == null ? "" : full.trim();
        if (text.startsWith("# ")) {
            int idx = text.indexOf('\n');
            if (idx > 0) {
                return new String[]{text.substring(2, idx).trim(), text.substring(idx).trim()};
            }
            return new String[]{text.substring(2).trim(), ""};
        }
        String t = StrUtil.isNotBlank(fallbackTitle) ? fallbackTitle : "AI 生成文章";
        return new String[]{t, text};
    }

    /** 提取 JSON 对象：去除代码围栏并截取首尾花括号。 */
    private JSONObject extractJsonObject(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int idx = s.indexOf('\n');
            s = idx >= 0 ? s.substring(idx + 1) : s;
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        try {
            return JSONUtil.parseObj(s);
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