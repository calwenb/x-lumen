package com.calwen.xlumen.ai.service.executor;

import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.TaskContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审校执行器单测（IDEA-025 F-0604，OPT-2/D20 全量迁移）：mock ChatRuntime——普通模式 Schema 兼容
 * （旧结果无证据字段合法）、事实核对模式（agent_enabled=1 走 chatWithTools + 可选证据字段）、重试、模型失败阻断。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ReviewExecutorTest {

    @Mock
    private ChatRuntime chatRuntime;

    @Mock
    private TaskContext ctx;

    private ReviewExecutor executor;
    private AiTaskEntity task;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = new ReviewExecutor(chatRuntime);
        task = new AiTaskEntity();
        task.setId(1L);
        task.setWorkspaceId(1L);
        task.setUserId(100L);
        task.setInputJson("{\"title\":\"部署指南\",\"content\":\"正文内容\"}");
    }

    private static String issueArray(boolean withEvidence) {
        String item = "{\"severity\":\"error\",\"position\":\"L3\",\"evidence\":\"术语不一致\",\"suggestion\":\"改为统一术语\"";
        if (withEvidence) {
            item += ",\"evidenceKnowledgeId\":\"2089895161090592769\",\"evidenceQuote\":\"库内定义\"";
        }
        return "[" + item + "}]";
    }

    @Test
    void nonAgent_schemaCompat_legacyResultWithoutEvidenceIsValid() {
        when(chatRuntime.resolveScene(1L, AiScene.REVIEWER))
                .thenReturn(SceneModel.builder().agentEnabled(false).build());
        when(chatRuntime.chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any()))
                .thenReturn(issueArray(false));

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"severity\":\"error\"").contains("\"suggestion\"");
        // 无证据字段列 → 不要求补
        assertThat(captor.getValue()).doesNotContain("evidenceKnowledgeId");
        verify(ctx).publishProgress(90);
    }

    @Test
    void agentMode_runsChatWithTools_withEvidenceFields() {
        when(chatRuntime.resolveScene(1L, AiScene.REVIEWER))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn("```json\n" + issueArray(true) + "\n```");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"evidenceKnowledgeId\":\"2089895161090592769\"")
                .contains("\"evidenceQuote\":\"库内定义\"");
    }

    @Test
    void agentMode_modelFailure_failsTask() {
        when(chatRuntime.resolveScene(1L, AiScene.REVIEWER))
                .thenReturn(SceneModel.builder().agentEnabled(true).build());
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂时不可用，请稍后重试"));

        assertThatThrownBy(() -> executor.execute(task, ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 服务不可用");
    }

    @Test
    void parseFailure_retriesOnce_thenFails() {
        when(chatRuntime.resolveScene(1L, AiScene.REVIEWER))
                .thenReturn(SceneModel.builder().agentEnabled(false).build());
        when(chatRuntime.chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any()))
                .thenReturn("不是 JSON");

        executor.execute(task, ctx);

        // 解析失败重试一次（共 2 次 chat），仍失败则任务 FAILED（F-0907 闸门）
        verify(chatRuntime, times(2)).chat(eq(1L), eq(AiScene.REVIEWER), any(), any(), any());
        verify(ctx).fail("审校输出必须是 JSON 数组");
    }
}