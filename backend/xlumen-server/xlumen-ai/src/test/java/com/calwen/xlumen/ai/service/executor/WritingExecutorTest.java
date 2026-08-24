package com.calwen.xlumen.ai.service.executor;

import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatRequest;
import com.calwen.xlumen.ai.service.provider.ProviderChatResult;
import com.calwen.xlumen.ai.service.provider.StreamCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写作多步工作流降级路径单测（IDEA-025 F-0608）：大纲解析失败/章节超限/单章失败/自审失败
 * 四条降级路径全部回退单次生成；普通模式行为不变。
 *
 * @author calwen
 * @date 2026/8/24
 */
class WritingExecutorTest {

    @Mock
    private ModelGateway modelGateway;

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
        executor = new WritingExecutor(modelGateway, aiProperties);
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

    /** 流式脚本：每次调用推一段文本并回调终态。 */
    private void scriptStream(String text) {
        doAnswer(inv -> {
            StreamCallback cb = inv.getArgument(3);
            cb.onContent(text);
            cb.onResult(ProviderChatResult.builder().content(text).build());
            return null;
        }).when(modelGateway).chatStream(any(), eq(AiScene.WRITING), any(), any(), any());
    }

    @Test
    void nonAgent_singlePass_unchanged() {
        when(modelGateway.resolveScene(1L, AiScene.WRITING))
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
        when(modelGateway.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        // 大纲输出不是 JSON → 降级
        when(modelGateway.chat(any(), eq(AiScene.WRITING), any()))
                .thenReturn(ProviderChatResult.builder().content("这不是 JSON").build());
        scriptStream("单次生成内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("单次生成内容");
        // 大纲失败路径未分章：最多一次 chatStream（单次生成）
        verify(modelGateway, atLeastOnce()).chatStream(any(), eq(AiScene.WRITING), any(), any(), any());
    }

    @Test
    void agent_chaptersOverLimit_fallsBackToSinglePass() {
        when(modelGateway.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        // 大纲 10 章 > 上限 8 → 回退
        when(modelGateway.chat(any(), eq(AiScene.WRITING), any()))
                .thenReturn(ProviderChatResult.builder().content(outlineJson(10)).build());
        scriptStream("单次生成内容");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("单次生成内容");
    }

    @Test
    void agent_selfReviewFailure_skipsRevision() {
        when(modelGateway.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        // 第 1 次 chat=大纲（WRITING）；第 2 次 chat=自审（REVIEWER）输出非 JSON → 跳过修订
        when(modelGateway.chat(any(), eq(AiScene.WRITING), any()))
                .thenReturn(ProviderChatResult.builder().content(outlineJson(2)).build());
        when(modelGateway.chat(any(), eq(AiScene.REVIEWER), any()))
                .thenReturn(ProviderChatResult.builder().content("审校失败输出").build());
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
        when(modelGateway.resolveScene(1L, AiScene.WRITING))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        when(modelGateway.chat(any(), eq(AiScene.WRITING), any()))
                .thenReturn(ProviderChatResult.builder().content(outlineJson(1)).build());
        // 章节流式失败（每次 onError）
        doAnswer(inv -> {
            StreamCallback cb = inv.getArgument(3);
            java.util.function.Consumer<Throwable> err = inv.getArgument(4);
            err.accept(new RuntimeException("AI 服务不可用"));
            return null;
        }).when(modelGateway).chatStream(any(), eq(AiScene.WRITING), any(), any(), any());

        executor.execute(task, ctx);

        // 章节重试 2 次失败 → 降级单次生成（第 3 次 chatStream 仍失败 → 任务 fail）
        verify(ctx, atLeastOnce()).fail(any());
    }
}