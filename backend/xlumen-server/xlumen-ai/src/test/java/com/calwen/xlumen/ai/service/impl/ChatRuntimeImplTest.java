package com.calwen.xlumen.ai.service.impl;

import cn.hutool.json.JSONObject;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.config.ScriptedChatModel;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.ai.service.tool.AgentTool;
import com.calwen.xlumen.ai.service.tool.AgentToolContext;
import com.calwen.xlumen.ai.service.tool.ToolEventPayload;
import com.calwen.xlumen.ai.service.tool.ToolEventSink;
import com.calwen.xlumen.ai.service.tool.ToolRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运行时集成单测（D20 全量迁移，离线铁律：不调真实模型）：ScriptedChatModel + ChatClient 全链路——
 * 无密钥脚本回退的 chat/chatStream，以及带工具的自动多轮循环（脚本「首轮工具调用 → 次轮正文」），
 * 验证 ChatGPTClient/ToolCallingAdvisor 在我们装配下真实执行工具并收集事件与配对。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ChatRuntimeImplTest {

    private ChatRuntimeImpl chatRuntime;
    private ScriptedChatModel scriptedChatModel;
    private ToolEventSink sink;

    /** 可控假工具：名字与脚本中的 tool_call 对齐。 */
    private static class FakeTool implements AgentTool {
        private final AtomicInteger calls = new AtomicInteger();

        int calls() {
            return calls.get();
        }

        @Override
        public String name() {
            return "knowledge.search";
        }

        @Override
        public String description() {
            return "测试检索工具";
        }

        @Override
        public String parametersSchema() {
            return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}";
        }

        @Override
        public Set<AiScene> scenes() {
            return Set.of(AiScene.QA);
        }

        @Override
        public String execute(AgentToolContext ctx, JSONObject args) {
            calls.incrementAndGet();
            return ToolEventPayload.okEnvelope("{\"title\":\"部署指南\",\"score\":0.9}");
        }
    }

    private FakeTool fakeTool;

    @BeforeEach
    void setUp() {
        SceneConfigService sceneConfigService = mock(SceneConfigService.class);
        when(sceneConfigService.resolve(anyLong(), eq(AiScene.QA)))
                .thenReturn(SceneModel.builder().providerName(null).model("mock").build());
        AiProperties aiProperties = new AiProperties();
        aiProperties.setAgentMaxToolCalls(8);
        aiProperties.setReviewerAgentMaxRounds(2);
        aiProperties.setAgentToolTimeoutMillis(2000);
        aiProperties.setAgentToolResultMaxChars(200);
        scriptedChatModel = new ScriptedChatModel();
        fakeTool = new FakeTool();
        sink = new ToolEventSink();
        chatRuntime = new ChatRuntimeImpl(sceneConfigService, aiProperties, scriptedChatModel,
                List.of(fakeTool));
    }

    @Test
    void chat_scriptedFallback_returnsFixedText() {
        String text = chatRuntime.chat(1L, AiScene.QA, List.of(new UserMessage("你好")), 0.7, 1024);
        assertThat(text).contains("模拟文章标题");
    }

    @Test
    void chatStream_emitsDeltas() {
        List<String> chunks = new ArrayList<>();
        chatRuntime.chatStream(1L, AiScene.QA, List.of(new UserMessage("你好")), 0.7, 1024,
                chunks::add, e -> {
                });
        assertThat(chunks).isNotEmpty();
        assertThat(String.join("", chunks)).contains("模拟文章标题");
    }

    @Test
    void chatWithTools_runAutomaticToolLoop_collectsEventsAndPairs() {
        // 脚本：第一轮返 assistant.tool_calls（knowledge.search），第二轮返最终正文
        AssistantMessage toolTurn = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "knowledge.search", "{\"query\":\"部署\"}")))
                .build();
        AssistantMessage finalTurn = AssistantMessage.builder().content("# 部署指南\n\n基于工具检索的回答。").build();
        scriptedChatModel.enqueueScript(
                new ChatResponse(List.of(new Generation(toolTurn))),
                new ChatResponse(List.of(new Generation(finalTurn))));

        ToolRun run = ToolRun.builder()
                .scene(AiScene.QA)
                .toolContext(AgentToolContext.builder().userId(1L).build())
                .sink(sink)
                .build();
        String text = chatRuntime.chatWithTools(1L, AiScene.QA, List.of(new UserMessage("部署怎么做")),
                0.7, 1024, run);

        assertThat(text).contains("基于工具检索的回答");
        // 工具真实执行了一次
        assertThat(fakeTool.calls()).isEqualTo(1);
        // 事件与配对完整收集
        assertThat(sink.getEvents()).hasSize(2);
        assertThat(sink.getEvents().get(0).getPhase()).isEqualTo("start");
        assertThat(sink.getEvents().get(1).getPhase()).isEqualTo("done");
        assertThat(sink.getEvents().get(1).getOk()).isTrue();
        assertThat(sink.getPairs()).hasSize(1);
        assertThat(sink.getPairs().get(0).getToolCallId()).isEqualTo("tc-1");
        assertThat(sink.getPairs().get(0).getName()).isEqualTo("knowledge.search");
    }
}