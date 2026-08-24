package com.calwen.xlumen.ai.service.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.enums.AiScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具适配器单测（OPT-2/D20 全量迁移）：信封语义、预算上限信封、超时错误信封、事件与配对收集
 * （替代原 ToolRegistryTest 的防护语义断言；工具循环本身由 ChatRuntimeImplTest 覆盖）。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ToolCallbackAdapterTest {

    private AiProperties aiProperties;
    private ToolEventSink sink;
    private ToolRun run;

    /** 可控假工具：直接返回 okEnvelope。 */
    private static class FakeTool implements AgentTool {
        @Override
        public String name() {
            return "knowledge.search";
        }

        @Override
        public String description() {
            return "测试工具";
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
            return ToolEventPayload.okEnvelope(JSONUtil.createArray().set(1).set(2));
        }
    }

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setAgentToolTimeoutMillis(2000);
        aiProperties.setAgentToolResultMaxChars(200);
        aiProperties.setAgentMaxToolCalls(8);
        sink = new ToolEventSink();
        run = ToolRun.builder()
                .scene(AiScene.QA)
                .toolContext(AgentToolContext.builder().userId(1L).build())
                .sink(sink)
                .build();
    }

    @Test
    void call_success_recordsEnvelopeEventsAndPair() {
        ToolCallbackAdapter adapter = new ToolCallbackAdapter(new FakeTool(), run, aiProperties,
                Executors.newSingleThreadExecutor());

        String envelope = adapter.call("{\"query\":\"部署\"}");

        assertThat(envelope).contains("\"ok\":true");
        // 事件轨迹：start + done
        assertThat(sink.getEvents()).hasSize(2);
        assertThat(sink.getEvents().get(0).getPhase()).isEqualTo("start");
        assertThat(sink.getEvents().get(0).getSeq()).isEqualTo(1);
        assertThat(sink.getEvents().get(1).getPhase()).isEqualTo("done");
        assertThat(sink.getEvents().get(1).getOk()).isTrue();
        // 落库配对：合成 tool_call_id
        assertThat(sink.getPairs()).hasSize(1);
        ToolPair pair = sink.getPairs().get(0);
        assertThat(pair.getToolCallId()).isEqualTo("tc-1");
        assertThat(pair.getName()).isEqualTo("knowledge.search");
        assertThat(pair.isOk()).isTrue();
    }

    @Test
    void call_liveListener_receivesStartAndDone() {
        java.util.List<ToolEvent> pushed = new java.util.ArrayList<>();
        ToolEventSink liveSink = new ToolEventSink(pushed::add);
        ToolRun liveRun = ToolRun.builder().scene(AiScene.QA)
                .toolContext(AgentToolContext.builder().build()).sink(liveSink).build();
        ToolCallbackAdapter adapter = new ToolCallbackAdapter(new FakeTool(), liveRun, aiProperties,
                Executors.newSingleThreadExecutor());

        adapter.call("{\"query\":\"x\"}");

        assertThat(pushed).hasSize(2);
        assertThat(pushed.get(0).getPhase()).isEqualTo("start");
        assertThat(pushed.get(1).getPhase()).isEqualTo("done");
    }

    @Test
    void call_overBudget_returnsCapEnvelopeWithoutExecuting() {
        // 预算 8：预置 8 次调用计数
        for (int i = 0; i < aiProperties.getAgentMaxToolCalls(); i++) {
            sink.incrementCalls();
        }
        ToolCallbackAdapter adapter = new ToolCallbackAdapter(new FakeTool(), run, aiProperties,
                Executors.newSingleThreadExecutor());

        String envelope = adapter.call("{\"query\":\"x\"}");

        assertThat(envelope).contains("\"ok\":false").contains("工具调用次数已达上限");
        // 超限仍落配对（模型需要该工具响应继续作答），但 ok=false 且有配对
        assertThat(sink.getPairs()).hasSize(1);
        assertThat(sink.getPairs().get(0).isOk()).isFalse();
        assertThat(sink.getPairs().get(0).getContent()).contains("工具调用次数已达上限");
    }

    @Test
    void call_toolTimeout_returnsTimeoutEnvelope() {
        AiProperties shortTimeout = new AiProperties();
        shortTimeout.setAgentToolTimeoutMillis(1);
        shortTimeout.setAgentToolResultMaxChars(200);
        shortTimeout.setAgentMaxToolCalls(8);
        AgentTool slowTool = new FakeTool() {
            @Override
            public String execute(AgentToolContext ctx, JSONObject args) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ToolEventPayload.okEnvelope("迟到");
            }
        };
        ToolCallbackAdapter adapter = new ToolCallbackAdapter(slowTool, run, shortTimeout,
                Executors.newSingleThreadExecutor());

        String envelope = adapter.call("{\"query\":\"x\"}");

        assertThat(envelope).contains("工具执行超时");
    }
}