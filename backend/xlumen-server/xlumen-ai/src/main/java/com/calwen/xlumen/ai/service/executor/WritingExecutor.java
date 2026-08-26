package com.calwen.xlumen.ai.service.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskExecutor;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.PromptResolver;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.util.AiJson;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 写作执行器（双轨合并后单轨）：统一走多步工作流 —— RAG 检索（可选）→ 大纲 → 分章流式 → 异源自审（REVIEWER）→ 修订，
 * 全程无人工确认断点。降级语义：主链路失败（大纲解析失败/章节超限/单章生成失败）→ 任务 FAILED；
 * 增强步骤失败（RAG 检索失败/自审失败/修订失败）→ 跳过对应增强继续交付。D20 全量迁移：
 * 链路改走 ChatRuntime（Spring AI 消息类型）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class WritingExecutor implements AiTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(WritingExecutor.class);

    /** RAG 检索条数上限。 */
    private static final int RAG_TOP_K = 6;

    private final ChatRuntime chatRuntime;
    private final AiProperties aiProperties;
    private final PromptResolver promptResolver;
    private final KnowledgeApi knowledgeApi;

    public WritingExecutor(ChatRuntime chatRuntime, AiProperties aiProperties,
                           PromptResolver promptResolver, KnowledgeApi knowledgeApi) {
        this.chatRuntime = chatRuntime;
        this.aiProperties = aiProperties;
        this.promptResolver = promptResolver;
        this.knowledgeApi = knowledgeApi;
    }

    @Override
    public AiScene scene() {
        return AiScene.WRITING;
    }

    @Override
    public void execute(AiTaskEntity task, TaskContext ctx) {
        JSONObject input = parseInput(task.getInputJson());
        // 双轨合并：写作一律走多步工作流，不再保留单次生成路径
        executeAgentWorkflow(task, ctx, input);
    }

    /** 多步工作流：RAG 检索（可选）→ 大纲 → 分章 → 自审 → 修订（主链路失败任务 FAILED，增强失败跳过交付）。 */
    private void executeAgentWorkflow(AiTaskEntity task, TaskContext ctx, JSONObject input) {
        String topic = input.getStr("topic");
        String draft = input.getStr("draft");
        String content = input.getStr("content");
        String title = input.getStr("title");

        // 步0 RAG 检索增强（可选启用；检索失败不阻断，按无引用写作降级）
        List<SearchResultDTO> references = ragRetrieve(task, topic, title);
        String referenceText = buildReferenceText(references);
        String userContextBase = buildUserPrompt(topic, draft, content, title);

        // 步1 大纲（非流式 chat 输出 JSON）
        ctx.publishProgress(10);
        List<JSONObject> chapters = outline(task, input, referenceText);
        if (chapters == null || chapters.isEmpty()) {
            log.info("写作多步：大纲解析失败，任务失败 taskId={}", task.getId());
            ctx.fail("大纲生成失败，请重试");
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
        String userContext = userContextBase + (StrUtil.isNotBlank(referenceText) ? "\n\n" + referenceText : "");
        for (int i = 0; i < chapters.size(); i++) {
            JSONObject ch = chapters.get(i);
            String chapterTitle = ch.getStr("title", "第 " + (i + 1) + " 章");
            ctx.publishChunk("\n\n--- 第 " + (i + 1) + "/" + chapters.size() + " 章：" + chapterTitle + " ---\n");
            String chapterText = generateChapter(task, ctx, userContext, outlineText.toString(), chapterTitle);
            if (chapterText == null) {
                log.info("写作多步：第 {} 章生成失败，任务失败 taskId={}", i + 1, task.getId());
                ctx.fail("第 " + (i + 1) + " 章生成失败，请重试");
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
                .set("reviewIssues", reviewJson == null ? "[]" : reviewJson)
                .set("references", referencesJson(references)));
        ctx.complete(resultJson);
    }

    /** RAG 检索：按主题/标题检索当前用户可见库，返回命中片段（按 [n] 顺序引用）。 */
    private List<SearchResultDTO> ragRetrieve(AiTaskEntity task, String topic, String title) {
        if (!aiProperties.isWritingRagEnabled() || task.getWorkspaceId() == null) {
            return List.of();
        }
        String query = StrUtil.blankToDefault(topic, StrUtil.blankToDefault(title, ""));
        if (StrUtil.isBlank(query)) {
            return List.of();
        }
        try {
            List<Long> kbIds = knowledgeApi.resolveVisibleKbIds(task.getUserId());
            if (kbIds == null || kbIds.isEmpty()) {
                return List.of();
            }
            return knowledgeApi.search(SearchRequestDTO.builder()
                    .workspaceId(task.getWorkspaceId())
                    .query(query)
                    .kbIds(kbIds)
                    .topK(RAG_TOP_K)
                    .build());
        } catch (Exception e) {
            log.warn("写作 RAG 检索失败，按无引用写作降级 taskId={}", task.getId(), e);
            return List.of();
        }
    }

    /** 引用块文本：注入用户消息，正文以 [n] 标注；锚点定位片段来源。 */
    private String buildReferenceText(List<SearchResultDTO> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("参考资料（来自知识库，正文引用以 [n] 标注）：\n");
        int i = 1;
        for (SearchResultDTO r : references) {
            String anchor = StrUtil.isNotBlank(r.getHeadingAnchor()) ? "【" + r.getHeadingAnchor() + "】" : "";
            sb.append("[").append(i++).append("] ").append(StrUtil.blankToDefault(r.getTitle(), ""))
                    .append(anchor).append("：")
                    .append(StrUtil.sub(StrUtil.blankToDefault(r.getChunkText(), ""), 0, 300)).append('\n');
        }
        return sb.toString();
    }

    /** 引用证据序列化（供前端溯源展示）。 */
    private String referencesJson(List<SearchResultDTO> references) {
        JSONArray arr = new JSONArray();
        int i = 1;
        for (SearchResultDTO r : references) {
            if (r.getKnowledgeId() == null) {
                continue;
            }
            arr.add(JSONUtil.createObj()
                    .set("refNo", i++)
                    .set("knowledgeId", r.getKnowledgeId())
                    .set("title", StrUtil.blankToDefault(r.getTitle(), ""))
                    .set("headingAnchor", StrUtil.blankToDefault(r.getHeadingAnchor(), ""))
                    .set("chunkText", StrUtil.sub(StrUtil.blankToDefault(r.getChunkText(), ""), 0, 200))
                    .set("score", r.getScore()));
        }
        return arr.toString();
    }

    /** 步1：大纲；解析失败或章节超限返回 null（回退单次）。 */
    private List<JSONObject> outline(AiTaskEntity task, JSONObject input, String referenceText) {
        String prompt = promptResolver.resolveWriting(task.getWorkspaceId(), "outline")
                .replace("{{MAX}}", String.valueOf(maxChapters()));
        String user = buildUserPrompt(input.getStr("topic"), input.getStr("draft"),
                input.getStr("content"), input.getStr("title"));
        if (StrUtil.isNotBlank(referenceText)) {
            user = user + "\n\n" + referenceText;
        }
        try {
            String content = chatRuntime.chat(task.getWorkspaceId(), AiScene.WRITING,
                    List.of(
                            new SystemMessage(prompt),
                            new UserMessage(user)),
                    0.4, 1024);
            JSONObject obj = AiJson.extractObject(content);
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
        String chapterPrompt = promptResolver.resolveWriting(task.getWorkspaceId(), "chapter")
                .replace("{{TITLE}}", chapterTitle);
        StringBuilder sb = new StringBuilder();
        for (int attempt = 0; attempt < 2; attempt++) {
            StringBuilder content = new StringBuilder();
            AtomicBoolean errored = new AtomicBoolean(false);
            String user = "全文大纲：" + outlineText + (StrUtil.isNotBlank(userContext) ? "\n" + userContext : "")
                    + "\n当前章节：" + chapterTitle;
            chatRuntime.chatStream(task.getWorkspaceId(), AiScene.WRITING,
                    List.of(
                            new SystemMessage(chapterPrompt),
                            new UserMessage(user)),
                    0.7, 2048,
                    delta -> {
                        content.append(delta);
                        ctx.publishChunk(delta);
                    },
                    err -> errored.set(true));
            if (!errored.get() && content.length() > 0) {
                sb.append(content);
                return sb.toString();
            }
        }
        return null;
    }

    /** 步3：自审（REVIEWER 异源）；失败/解析失败返回 null（跳过修订）。 */
    private String selfReview(AiTaskEntity task, String fullText) {
        try {
            String content = chatRuntime.chat(task.getWorkspaceId(), AiScene.REVIEWER,
                    List.of(
                            new SystemMessage(promptResolver.resolveWriting(task.getWorkspaceId(), "self_review")),
                            new UserMessage(fullText)),
                    0.2, 2048);
            JSONArray arr = AiJson.extractArray(content);
            if (arr == null) {
                return null;
            }
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
        chatRuntime.chatStream(task.getWorkspaceId(), AiScene.WRITING,
                List.of(
                        new SystemMessage(promptResolver.resolveWriting(task.getWorkspaceId(), "revise")),
                        new UserMessage("审校意见：\n" + reviewJson + "\n\n全文初稿：\n" + fullText)),
                0.5, 4096,
                delta -> {
                    sb.append(delta);
                    ctx.publishChunk(delta);
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

}