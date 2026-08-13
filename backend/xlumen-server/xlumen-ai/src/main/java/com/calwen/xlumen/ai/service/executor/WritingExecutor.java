package com.calwen.xlumen.ai.service.executor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskExecutor;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 写作执行器（F-0601）：流式调用网关，chunk 推 SSE，完成后解析标题与正文写入 resultJson。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Component
public class WritingExecutor implements AiTaskExecutor {

    /** 写作 System 提示词：明确角色与输出格式。 */
    private static final String SYSTEM_PROMPT = "你是小光，一名专业的中文内容创作助手。"
            + "请根据用户提供的主题、草稿或素材，输出一篇结构完整、带标题的完整 Markdown 文章。"
            + "第一行必须是 # 标题，其余为正文内容。";

    private final ModelGateway modelGateway;

    public WritingExecutor(ModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public AiScene scene() {
        return AiScene.WRITING;
    }

    @Override
    public void execute(AiTaskEntity task, TaskContext ctx) {
        JSONObject input = parseInput(task.getInputJson());
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
                chunk -> {
                    sb.append(chunk);
                    ctx.publishChunk(chunk);
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
