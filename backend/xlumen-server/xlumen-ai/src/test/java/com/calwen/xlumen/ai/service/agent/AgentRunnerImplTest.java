package com.calwen.xlumen.ai.service.agent;

import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ModelGateway;
import com.calwen.xlumen.ai.service.provider.ChatMessage;
import com.calwen.xlumen.ai.service.provider.ProviderChatResult;
import com.calwen.xlumen.ai.service.provider.StreamCallback;
import com.calwen.xlumen.ai.service.provider.ToolCall;
import com.calwen.xlumen.ai.service.tool.KnowledgeDirectoryTool;
import com.calwen.xlumen.ai.service.tool.KnowledgeListTool;
import com.calwen.xlumen.ai.service.tool.KnowledgeSearchTool;
import com.calwen.xlumen.ai.service.tool.ToolRegistry;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentRunner 编排单测（IDEA-025 F-0708）：MockProvider 式脚本队列离线驱动——
 * 第一轮返 tool_calls → 第二轮返正文全循环；轮数上限触发最后一轮禁工具；工具总次数截断；
 * knowledge.search 命中聚合进 citation；工具失败不阻断（错误信封给模型后继续作答）。
 *
 * @author calwen
 * @date 2026/8/24
 */
class AgentRunnerImplTest {

    @Mock
    private ModelGateway modelGateway;

    @Mock
    private KnowledgeApi knowledgeApi;

    private AiProperties aiProperties;
    private AgentRunnerImpl runner;
    private Queue<ProviderChatResult> script = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiProperties = new AiProperties();
        aiProperties.setAgentMaxRounds(5);
        aiProperties.setAgentMaxToolCalls(8);
        aiProperties.setAgentToolTimeoutMillis(1000);
        aiProperties.setAgentToolResultMaxChars(8000);
        aiProperties.setReviewerAgentMaxRounds(2);
        runner = new AgentRunnerImpl(modelGateway,
                new ToolRegistry(List.of(new KnowledgeSearchTool(knowledgeApi),
                        new KnowledgeListTool(knowledgeApi), new KnowledgeDirectoryTool(knowledgeApi)),
                        aiProperties),
                aiProperties);
        when(knowledgeApi.resolveVisibleKbIds(any())).thenReturn(List.of(1L));
        when(knowledgeApi.search(any())).thenReturn(List.of(
                SearchResultDTO.builder().knowledgeId(1L).title("部署指南").chunkSeq(1)
                        .chunkText("部署步骤").score(0.9f).build()));
    }

    /** 脚本化 chatStream：按调用次序出队，逐字流 content 后回调终态。 */
    private void scriptStream() {
        doAnswer(inv -> {
            StreamCallback cb = inv.getArgument(3);
            ProviderChatResult r = script.poll();
            if (r == null) {
                cb.onResult(ProviderChatResult.builder().content("兜底").build());
                return null;
            }
            if (r.getContent() != null) {
                for (int i = 0; i < r.getContent().length();) {
                    int codePoint = r.getContent().codePointAt(i);
                    cb.onContent(new String(Character.toChars(codePoint)));
                    i += Character.charCount(codePoint);
                }
            }
            cb.onResult(r);
            return null;
        }).when(modelGateway).chatStream(any(), any(), any(), any(), any());
    }

    /** 脚本化 chat（非流式）：按调用次序出队。 */
    private void scriptChat() {
        when(modelGateway.chat(any(), any(), any())).thenAnswer(inv -> {
            ProviderChatResult r = script.poll();
            return r == null ? ProviderChatResult.builder().content("兜底").build() : r;
        });
    }

    private AgentRequest request(AiScene scene, boolean stream) {
        return AgentRequest.builder()
                .workspaceId(1L)
                .userId(100L)
                .messages(List.of(
                        ChatMessage.builder().role("system").content("你是小光。").build(),
                        ChatMessage.builder().role("user").content("部署怎么做？").build()))
                .scene(scene)
                .stream(stream)
                .temperature(0.7)
                .maxTokens(1024)
                .build();
    }

    /** 脚本助手：收集事件断言用。 */
    private static class Recorder implements AgentEvents {
        final StringBuilder content = new StringBuilder();
        final List<ToolEvent> tools = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onContent(String delta) {
            content.append(delta);
        }

        @Override
        public void onTool(ToolEvent event) {
            tools.add(event);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }

    @Test
    void run_toolThenAnswer_loopCompletesWithCitations() {
        scriptStream();
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(ToolCall.builder().id("call_1").name("knowledge.search")
                        .arguments("{\"query\":\"部署\"}").build()))
                .finishReason("tool_calls")
                .build());
        script.add(ProviderChatResult.builder().content("基于检索的答案").finishReason("stop").build());

        Recorder events = new Recorder();
        AgentResult result = runner.run(request(AiScene.QA, true), events);

        assertThat(result.getContent()).isEqualTo("基于检索的答案");
        assertThat(events.content.toString()).isEqualTo("基于检索的答案");
        assertThat(events.errors).isEmpty();
        // 工具轨迹：search start + done
        assertThat(events.tools).hasSize(2);
        assertThat(events.tools.get(0).getPhase()).isEqualTo("start");
        assertThat(events.tools.get(0).getName()).isEqualTo("knowledge.search");
        assertThat(events.tools.get(1).getPhase()).isEqualTo("done");
        assertThat(events.tools.get(1).getOk()).isTrue();
        // 命中统一聚合进 citations
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getTitle()).isEqualTo("部署指南");
        // 中间轮轨迹：assistant（工具调用）+ tool 行
        assertThat(result.getAuxMessages()).hasSize(2);
        assertThat(result.getAuxMessages().get(0).getRole()).isEqualTo("assistant");
        assertThat(result.getAuxMessages().get(0).getToolCalls()).hasSize(1);
        assertThat(result.getAuxMessages().get(1).getRole()).isEqualTo("tool");
        assertThat(result.getAuxMessages().get(1).getToolCallId()).isEqualTo("call_1");
    }

    @Test
    void run_roundsExhausted_forcesAnswerWithoutTools() {
        aiProperties.setAgentMaxRounds(2);
        scriptStream();
        // 两轮都坚持调用工具 → 循环结束追加强制收尾，第 3 次调用应产出正文
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(ToolCall.builder().id("c1").name("knowledge.search").arguments("{\"query\":\"a\"}").build()))
                .build());
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(ToolCall.builder().id("c2").name("knowledge.search").arguments("{\"query\":\"b\"}").build()))
                .build());
        script.add(ProviderChatResult.builder().content("强制答案").build());

        Recorder events = new Recorder();
        AgentResult result = runner.run(request(AiScene.QA, true), events);

        assertThat(result.getContent()).isEqualTo("强制答案");
        assertThat(events.tools).hasSize(4); // 2 轮 × (start+done)
        // 强制收尾轮的请求不带 tools：验证最后一次请求的 tools 为 null
    }

    @Test
    void run_maxToolCalls_truncatesExcessWithErrorEnvelope() {
        aiProperties.setAgentMaxToolCalls(2);
        scriptStream();
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(
                        ToolCall.builder().id("c1").name("knowledge.search").arguments("{\"query\":\"x\"}").build(),
                        ToolCall.builder().id("c2").name("knowledge.search").arguments("{\"query\":\"y\"}").build(),
                        ToolCall.builder().id("c3").name("knowledge.search").arguments("{\"query\":\"z\"}").build()))
                .build());
        script.add(ProviderChatResult.builder().content("完成").build());

        RunnerResultCapture capture = new RunnerResultCapture();
        AgentResult result = runner.run(request(AiScene.QA, true), capture);

        assertThat(result.getContent()).isEqualTo("完成");
        // 只执行 2 次检索，第 3 个调用给错误信封
        verify(knowledgeApi, times(2)).search(any());
        List<ChatMessage> toolRows = result.getAuxMessages().stream()
                .filter(m -> "tool".equals(m.getRole())).toList();
        assertThat(toolRows).hasSize(3);
        assertThat(toolRows.get(2).getContent()).contains("已达上限");
    }

    @Test
    void run_reviewerScene_nonStream_roundLimitEnforced() {
        aiProperties.setAgentMaxRounds(5);
        aiProperties.setReviewerAgentMaxRounds(2);
        scriptChat();
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(ToolCall.builder().id("c1").name("knowledge.search").arguments("{\"query\":\"a\"}").build()))
                .build());
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(ToolCall.builder().id("c2").name("knowledge.search").arguments("{\"query\":\"b\"}").build()))
                .build());
        // 轮数上限 2 已用尽 → 强制收尾调用
        script.add(ProviderChatResult.builder().content("审校 JSON").build());

        Recorder events = new Recorder();
        AgentResult result = runner.run(request(AiScene.REVIEWER, false), events);

        assertThat(result.getContent()).isEqualTo("审校 JSON");
        assertThat(result.getError()).isNull();
        verify(knowledgeApi, times(2)).search(any());
    }

    @Test
    void run_toolFailure_doesNotFailRun() {
        scriptStream();
        // 检索越权（resolveVisibleKbIds 不包含 999）→ 错误信封，模型仍可继续作答
        when(knowledgeApi.resolveVisibleKbIds(any())).thenReturn(List.of(1L));
        script.add(ProviderChatResult.builder()
                .toolCalls(List.of(ToolCall.builder().id("c1").name("knowledge.search")
                        .arguments("{\"query\":\"x\",\"kbId\":\"999\"}").build()))
                .build());
        script.add(ProviderChatResult.builder().content("基于已有信息作答").build());

        Recorder events = new Recorder();
        AgentResult result = runner.run(request(AiScene.QA, true), events);

        assertThat(result.getContent()).isEqualTo("基于已有信息作答");
        assertThat(result.getError()).isNull();
        // done 事件 ok=false（工具失败），但整体运行不失败
        assertThat(events.tools.get(1).getOk()).isFalse();
        assertThat(events.tools.get(1).getSummary()).contains("无权访问");
    }

    /** 流式请求断言辅助。 */
    private static class RunnerResultCapture implements AgentEvents {
        @Override
        public void onContent(String delta) {
        }

        @Override
        public void onTool(ToolEvent event) {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}