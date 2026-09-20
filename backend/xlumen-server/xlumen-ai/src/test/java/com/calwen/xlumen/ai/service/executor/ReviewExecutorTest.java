package com.calwen.xlumen.ai.service.executor;

import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.ai.service.PromptResolver;
import com.calwen.xlumen.ai.service.SceneConfigService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审校执行器单测（双轨合并后单轨）：统一走 ChatRuntime 工具化非流式（chatWithTools）——
 * Schema 兼容（旧结果无证据字段合法）、事实核对（可选证据字段）、模型失败阻断、解析失败重试。
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
        SceneConfigService sceneConfigService = mock(SceneConfigService.class);
        when(sceneConfigService.resolve(anyLong(), any())).thenReturn(SceneModel.builder().build());
        executor = new ReviewExecutor(chatRuntime, new PromptResolver(sceneConfigService));
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
    void resultWithoutEvidenceFields_isValid() {
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
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
    void chatWithTools_resultWithEvidenceFields() {
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn("```json\n" + issueArray(true) + "\n```");

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"evidenceKnowledgeId\":\"2089895161090592769\"")
                .contains("\"evidenceQuote\":\"库内定义\"");
    }

    @Test
    void modelFailure_failsTask() {
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂时不可用，请稍后重试"));

        assertThatThrownBy(() -> executor.execute(task, ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 服务不可用");
    }

    @Test
    void parseFailure_retriesOnce_thenFails() {
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn("不是 JSON");

        executor.execute(task, ctx);

        // 解析失败重试一次（共 2 次 chatWithTools），仍失败则任务 FAILED
        verify(chatRuntime, times(2)).chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any());
        verify(ctx).fail("审校输出必须是 JSON 数组");
    }

    @Test
    void shortContent_stillSingleCall() {
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn(issueArray(false));

        executor.execute(task, ctx);

        // 短内容不分片：一次调用即得结论
        verify(chatRuntime, times(1)).chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any());
        verify(ctx).complete(anyString());
    }

    @Test
    void longContent_shardedIntoMultipleCalls() {
        // 超过分片阈值（6000 字符）的长文 → 多段分别审校并合并
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            longText.append("## 第 ").append(i + 1).append(" 节\n").append("内容".repeat(900)).append("\n\n");
        }
        task.setInputJson("{\"title\":\"长文\",\"content\":\"" + longText.toString().replace("\n", "\\n") + "\"}");
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn(issueArray(false));

        executor.execute(task, ctx);

        verify(chatRuntime, atLeast(2)).chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        // 分片各自命中同一问题 → 合并去重后仅一条
        assertThat(captor.getValue()).contains("\"position\":\"L3\"");
    }

    @Test
    void truncatedArray_salvagesCompleteIssues() {
        String truncated = "[{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"术语不一致\","
                + "\"suggestion\":\"改为统一术语\"},{\"severity\":\"warning\",\"position\":\"L2\",\"eviden";
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn(truncated);

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"position\":\"L1\"");
        assertThat(captor.getValue()).doesNotContain("L2");
        verify(ctx, never()).fail(anyString());
    }

    @Test
    void invalidElement_skippedButValidKept() {
        String mixed = "[{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"E1\",\"suggestion\":\"S1\"},"
                + "{\"severity\":\"warning\"},"
                + "{\"severity\":\"info\",\"position\":\"L9\",\"evidence\":\"E9\",\"suggestion\":\"S9\"}]";
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn(mixed);

        executor.execute(task, ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).complete(captor.capture());
        assertThat(captor.getValue()).contains("\"position\":\"L1\"").contains("\"position\":\"L9\"");
        assertThat(captor.getValue()).doesNotContain("\"warning\"");
    }

    @Test
    void allElementsInvalid_treatedAsFailureNotCleanPass() {
        // 数组非空但无一个合法元素 → 不能当作「无问题」结论，重试后仍失败 → 任务 FAILED
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn("[{\"severity\":\"warning\"}]");

        executor.execute(task, ctx);

        verify(ctx).fail("审校输出必须是 JSON 数组");
        verify(ctx, never()).complete(anyString());
    }

    @Test
    void longContent_allShardsFail_thenTaskFails() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            longText.append("内容".repeat(500)).append("\n\n");
        }
        task.setInputJson("{\"title\":\"长文\",\"content\":\"" + longText.toString().replace("\n", "\\n") + "\"}");
        when(chatRuntime.chatWithTools(eq(1L), eq(AiScene.REVIEWER), any(), any(), any(), any()))
                .thenReturn("不是 JSON");

        executor.execute(task, ctx);

        // 分片全部失败才算任务失败
        verify(ctx).fail("审校输出必须是 JSON 数组");
        verify(ctx, never()).complete(anyString());
    }
}
