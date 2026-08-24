package com.calwen.xlumen.ai.service.executor;

import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写作多步工作流降级路径单测（IDEA-025 F-0608，OPT-2/D20 全量迁移）：mock ChatRuntime——大纲解析失败/
 * 章节超限/单章失败/自审失败四条降级路径全部回退单次生成；普通模式行为不变。
 *
 * @author calwen
 * @date 2026/8/24
 */
class WritingExecutorTest {

    @Mock
    private ChatRuntime chatRuntime;

    @Mock
    private TaskContext ctx;

    private AiProperties aiProperties;
    private WritingExecutor executor;
    private AiTaskEntity task;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiProperties = new AiProperties();
        aiProperties.setWritingMaxChapters(8);
        executor = new WritingExecutor(chatRuntime, aiProperties);
        task = new AiTaskEntity();
        task.setId(1L);
        task.setWorkspaceId(1L);
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

    /** 流式脚本：每次调用推一段文本且不触发 onError。 */
    private void scriptStream(String text) {
        doAnswer(inv -> {
            java.util.function.Consumer<String> onChunk = inv.getArgument(5);
            onChunk.accept(text);
            return null;
        }).when(chatRuntime).chatStream(eq(1L), eq(AiScene.WRITING), any(), any(), any(), any(), any());
    }

    @Test
    void nonAgent_singlePass_unchanged() {
        when(chatRuntime.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(false).build());
        scriptStream("# 模拟文章标题\n\n正文内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("模拟文章标题").contains("正文内容");
        verify(ctx, never()).fail(any());
    }

    @Test
    void agent_outlineParseFailure_fallsBackToSinglePass() {
        when(chatRuntime.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        // 大纲输出不是 JSON → 降级
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn("这不是 JSON");
        scriptStream("单次生成内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("单次生成内容");
        // 大纲失败路径未分章：至少一次 chatStream（单次生成）
        verify(chatRuntime, atLeastOnce()).chatStream(eq(1L), eq(AiScene.WRITING), any(), any(), any(), any(), any());
    }

    @Test
    void agent_chaptersOverLimit_fallsBackToSinglePass() {
        when(chatRuntime.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        // 大纲 10 章 > 上限 8 → 回退
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(10));
        scriptStream("单次生成内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("单次生成内容");
    }

    @Test
    void agent_selfReviewFailure_skipsRevision() {
        when(chatRuntime.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        // 第 1 次 chat=大纲（WRITING）；第 2 次 chat=自审（REVIEWER）输出非 JSON → 跳过修订
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(2));
        when(chatRuntime.chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any()))
                .thenReturn("审校失败输出");
        scriptStream("第 1 章内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("第 1 章内容");
        // 自审失败 → 意见快照为空数组
        assertThat(captor.getValue()).contains("\"reviewIssues\":\"[]\"");
    }

    @Test
    void agent_chapterFailureRetriesThenFallsBackToSinglePass() {
        when(chatRuntime.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), any(), any(), any()))
                .thenReturn(outlineJson(1));
        // 章节流式失败（每次 onError）
        doAnswer(inv -> {
            java.util.function.Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException("AI 服务不可用"));
            return null;
        }).when(chatRuntime).chatStream(eq(1L), eq(AiScene.WRITING), any(), any(), any(), any(), any());

        executor.execute(task, ctx);

        // 章节重试 2 次失败 → 降级单次生成（第 3 次 chatStream 仍失败 → 任务 fail）
        verify(ctx, atLeastOnce()).fail(any());
    }
}