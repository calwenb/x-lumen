package com.calwen.xlumen.ai.service.executor;

import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.PromptResolver;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写作多步工作流单测（双轨合并后单轨）：mock ChatRuntime——多步主链路成功，
 * 主链路失败（大纲解析失败/章节超限/单章失败）→ 任务 FAILED；增强失败（自审失败）→ 跳过修订交付初稿。
 *
 * @author calwen
 * @date 2026/8/24
 */
class WritingExecutorTest {

    @Mock
    private ChatRuntime chatRuntime;

    @Mock
    private TaskContext ctx;

    @Mock
    private KnowledgeApi knowledgeApi;

    private AiProperties aiProperties;
    private WritingExecutor executor;
    private AiTaskEntity task;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiProperties = new AiProperties();
        aiProperties.setWritingMaxChapters(8);
        aiProperties.setWritingRagEnabled(false);
        SceneConfigService sceneConfigService = mock(SceneConfigService.class);
        when(sceneConfigService.resolve(anyLong(), any())).thenReturn(SceneModel.builder().build());
        executor = new WritingExecutor(chatRuntime, aiProperties, new PromptResolver(sceneConfigService), knowledgeApi);
        task = new AiTaskEntity();
        task.setId(1L);
        task.setWorkspaceId(1L);
        task.setUserId(100L);
        task.setInputJson("{\"topic\":\"部署指南\"}");
    }

    private static String outlineJson(int chapters) {
        StringBuilder sb = new StringBuilder("{\"chapters\":[");
        for (int i = 0; i < chapters; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"title\":\"第").append(i + 1).append("章\",\"points\":[\"要点\"]}");
        }
        return sb.append("]}").toString();
    }

    private static final String REVIEW_JSON =
            "[{\"severity\":\"info\",\"position\":\"L1\",\"evidence\":\"证据\",\"suggestion\":\"建议\"}]";

    /** 流式脚本：每次调用推一段文本且不触发 onError（分章与修订共走 chatStream/WRITING）。 */
    private void scriptStream(String text) {
        doAnswer(inv -> {
            java.util.function.Consumer<String> onChunk = inv.getArgument(5);
            onChunk.accept(text);
            return null;
        }).when(chatRuntime).chatStream(eq(1L), eq(AiScene.WRITING), any(), any(), any(), any(), any());
    }

    @Test
    void multiStep_happyPath_outline_chapters_review_revise() {
        // 大纲（WRITING）+ 自审（REVIEWER）各自按场景 stubbing
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(2));
        when(chatRuntime.chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any()))
                .thenReturn(REVIEW_JSON);
        scriptStream("第 N 章正文");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        // 分章文本拼接进正文
        assertThat(captor.getValue()).contains("第 N 章正文");
        // 大纲快照与审校意见入库
        assertThat(captor.getValue()).contains("\"outline\"");
        assertThat(captor.getValue()).contains("\"reviewIssues\"");
        verify(ctx, never()).fail(anyString());
    }

    @Test
    void outlineParseFailure_failsTask() {
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn("这不是 JSON");

        executor.execute(task, ctx);

        verify(ctx).fail("大纲生成失败，请重试");
        verify(ctx, never()).complete(anyString());
    }

    @Test
    void chaptersOverLimit_failsTask() {
        // 大纲 10 章 > 上限 8 → outline() 返回 null → 任务失败
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(10));

        executor.execute(task, ctx);

        verify(ctx).fail("大纲生成失败，请重试");
        verify(ctx, never()).complete(anyString());
    }

    @Test
    void selfReviewFailure_skipsRevisionAndCompletes() {
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(2));
        // 自审（REVIEWER）输出非 JSON → 跳过修订，直接交付初稿
        when(chatRuntime.chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any()))
                .thenReturn("审校失败输出");
        scriptStream("第 1 章内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("第 1 章内容");
        // 自审失败 → 意见快照为空数组，不阻断交付
        assertThat(captor.getValue()).contains("\"reviewIssues\":\"[]\"");
    }

    @Test
    void chapterFailure_failsTask() {
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        // 分章流式每次 onError → 重试 2 次仍失败 → 任务失败
        doAnswer(inv -> {
            java.util.function.Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException("AI 服务不可用"));
            return null;
        }).when(chatRuntime).chatStream(eq(1L), eq(AiScene.WRITING), any(), any(), any(), any(), any());

        executor.execute(task, ctx);

        verify(ctx).fail(anyString());
        verify(ctx, never()).complete(anyString());
    }

    @Test
    void ragEnabled_injectsReferencesIntoOutlineAndResult() {
        aiProperties.setWritingRagEnabled(true);
        when(knowledgeApi.resolveVisibleKbIds(anyLong())).thenReturn(List.of(10L));
        when(knowledgeApi.search(any(SearchRequestDTO.class))).thenReturn(List.of(
                SearchResultDTO.builder().knowledgeId(100L).title("部署手册")
                        .headingAnchor("安装").chunkText("安装部署步骤").score(0.8f).build()));
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(2));
        when(chatRuntime.chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any()))
                .thenReturn(REVIEW_JSON);
        scriptStream("第 N 章正文");

        executor.execute(task, ctx);

        // 大纲用户消息携带参考资料
        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatRuntime).chat(eq(1L), eq(AiScene.WRITING), messages.capture(), any(), any());
        String userText = messages.getValue().stream()
                .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage um && um.getText() != null)
                .map(m -> ((org.springframework.ai.chat.messages.UserMessage) m).getText())
                .reduce("", String::concat);
        assertThat(userText).contains("参考资料").contains("部署手册");
        // 结果携带引用证据
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"references\"");
        assertThat(captor.getValue()).contains("部署手册");
        // references 为 JSON 内嵌字符串，键值引号被转义
        assertThat(captor.getValue()).contains("\\\"knowledgeId\\\":100");
    }

    @Test
    void ragDisabled_skipsRetrieval() {
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        scriptStream("正文");

        executor.execute(task, ctx);

        verify(knowledgeApi, never()).search(any(SearchRequestDTO.class));
    }

    @Test
    void ragEnabled_searchRequestHasNoWorkspaceClause() {
        aiProperties.setWritingRagEnabled(true);
        when(knowledgeApi.resolveVisibleKbIds(anyLong())).thenReturn(List.of(10L, 20L));
        when(knowledgeApi.search(any(SearchRequestDTO.class))).thenReturn(List.of());
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        scriptStream("正文");

        executor.execute(task, ctx);

        // 写作 RAG 与对话检索同口径：只按可见库集合过滤，不附加 workspace（跨空间公开知识可召回）
        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(knowledgeApi).search(captor.capture());
        assertThat(captor.getValue().getWorkspaceId()).isNull();
        assertThat(captor.getValue().getKbIds()).containsExactly(10L, 20L);
    }

    @Test
    void topicOnly_fallsBackToTopicAsTitle() {
        // 只给主题、产出正文无 # 标题行 → 标题取主题，不得退化为通用占位
        task.setInputJson("{\"topic\":\"分布式事务 Seata AT 模式常见坑\"}");
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        scriptStream("正文内容，没有标题行");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("分布式事务 Seata AT 模式常见坑");
        assertThat(captor.getValue()).doesNotContain("AI 生成文章");
    }

    @Test
    void contentHeadingWinsOverInputTitleAndTopic() {
        task.setInputJson("{\"topic\":\"主题\",\"title\":\"输入标题\"}");
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        scriptStream("# 产出标题\n正文内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        // 既有行为不变：产出首个 # 标题优先于输入 title/topic
        assertThat(captor.getValue()).contains("\"title\":\"产出标题\"");
    }

    @Test
    void inputTitlePreferredOverTopicWhenNoHeading() {
        task.setInputJson("{\"topic\":\"主题\",\"title\":\"输入标题\"}");
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        scriptStream("正文内容，没有标题行");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"title\":\"输入标题\"");
    }
}
