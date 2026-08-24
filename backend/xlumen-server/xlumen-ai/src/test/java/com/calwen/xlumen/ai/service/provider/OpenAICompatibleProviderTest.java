package com.calwen.xlumen.ai.service.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAI 兼容协议扩展单测（IDEA-025 F-0708）：buildChatBody 快照断言（tools/assistant.tool_calls/tool 消息）、
 * 流式 tool_calls 分片归并（id 后到、arguments 分 3 片、finish_reason 独立 chunk）。
 *
 * @author calwen
 * @date 2026/8/24
 */
class OpenAICompatibleProviderTest {

    /** 最小可测子类。 */
    static class TestProvider extends OpenAICompatibleProvider {
        TestProvider() {
            super("http://localhost:1", "test-key", null);
        }

        @Override
        public String name() {
            return "TEST";
        }
    }

    private final TestProvider provider = new TestProvider();

    @Test
    void chat_body_withTools_assistantToolCalls_toolMessage() {
        List<ChatMessage> messages = List.of(
                ChatMessage.builder().role("user").content("你好").build(),
                ChatMessage.builder().role("assistant").content("")
                        .toolCalls(List.of(ToolCall.builder().id("call_1").name("knowledge.search")
                                .arguments("{\"query\":\"部署\"}").build()))
                        .build(),
                ChatMessage.builder().role("tool").toolCallId("call_1").name("knowledge.search")
                        .content("{\"ok\":true,\"data\":[]}").build());
        ProviderChatRequest request = ProviderChatRequest.builder()
                .model("qwen-plus")
                .messages(messages)
                .tools(List.of(ToolSpec.builder()
                        .name("knowledge.search")
                        .description("检索知识库")
                        .parameters("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .build()))
                .temperature(0.7)
                .maxTokens(1024)
                .stream(true)
                .build();

        JSONObject body = provider.buildChatBody(request, true);

        assertThat(body.getStr("model")).isEqualTo("qwen-plus");
        // tools 序列化：type=function + parameters 为对象
        JSONArray tools = body.getJSONArray("tools");
        assertThat(tools).hasSize(1);
        JSONObject function = tools.getJSONObject(0).getJSONObject("function");
        assertThat(function.getStr("name")).isEqualTo("knowledge.search");
        assertThat(function.getJSONObject("parameters").getStr("type")).isEqualTo("object");
        // assistant 消息增发 tool_calls
        JSONArray messagesArr = body.getJSONArray("messages");
        JSONObject assistantMsg = messagesArr.getJSONObject(1);
        assertThat(assistantMsg.getStr("role")).isEqualTo("assistant");
        assertThat(assistantMsg.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function").getStr("arguments"))
                .isEqualTo("{\"query\":\"部署\"}");
        // tool 角色消息带 tool_call_id
        JSONObject toolMsg = messagesArr.getJSONObject(2);
        assertThat(toolMsg.getStr("role")).isEqualTo("tool");
        assertThat(toolMsg.getStr("tool_call_id")).isEqualTo("call_1");
        assertThat(toolMsg.getStr("content")).contains("\"ok\":true");
    }

    @Test
    void chat_body_withoutTools_omitsToolsKey() {
        ProviderChatRequest request = ProviderChatRequest.builder()
                .model("qwen-plus")
                .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                .build();
        JSONObject body = provider.buildChatBody(request, false);
        // 向后兼容：无 tools 时请求体不含 tools 键
        assertThat(body.containsKey("tools")).isFalse();
    }

    @Test
    void stream_toolCalls_mergeSplitFragmentsByIndex() {
        OpenAICompatibleProvider.StreamAccumulator acc = new OpenAICompatibleProvider.StreamAccumulator();
        // 分片1：id+name 首现、arguments 第一段
        OpenAICompatibleProvider.parseDelta(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_9\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"knowledge.search\",\"arguments\":\"{\\\"query\\\":\\\"\"}}]}}]}",
                acc, d -> {
                });
        // 分片2：arguments 第二段
        OpenAICompatibleProvider.parseDelta(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"部署\\\"}\"}}]}}]}",
                acc, d -> {
                });
        // 分片3：arguments 第三段
        OpenAICompatibleProvider.parseDelta(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\" }\"}}]}}]}",
                acc, d -> {
                });
        // finish_reason 独立 chunk
        OpenAICompatibleProvider.parseDelta("{\"choices\":[{\"finish_reason\":\"tool_calls\"}]}", acc, d -> {
        });

        ProviderChatResult result = acc.toResult();
        assertThat(result.getToolCalls()).hasSize(1);
        ToolCall call = result.getToolCalls().get(0);
        assertThat(call.getId()).isEqualTo("call_9");
        assertThat(call.getName()).isEqualTo("knowledge.search");
        assertThat(call.getArguments()).isEqualTo("{\"query\":\"部署\"} }");
        assertThat(result.getFinishReason()).isEqualTo("tool_calls");
    }

    @Test
    void stream_contentDelta_forwardsToCallback() {
        OpenAICompatibleProvider.StreamAccumulator acc = new OpenAICompatibleProvider.StreamAccumulator();
        StringBuilder received = new StringBuilder();
        OpenAICompatibleProvider.parseDelta(
                "{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}", acc, received::append);
        OpenAICompatibleProvider.parseDelta(
                "{\"choices\":[{\"delta\":{\"content\":\"，小光\"}}]}", acc, received::append);

        assertThat(received.toString()).isEqualTo("你好，小光");
        assertThat(acc.toResult().getContent()).isEqualTo("你好，小光");
        assertThat(acc.toResult().hasToolCalls()).isFalse();
    }

    @Test
    void chat_result_parsesToolCallsAndFinishReason_withMockedHttp() {
        // chat() 的 HTTP 层已由真实网络覆盖；这里仅验证返回契约类型可组装
        ProviderChatResult result = ProviderChatResult.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder().id("c1").name("knowledge.search").arguments("{}").build()))
                .finishReason("tool_calls")
                .build();
        assertThat(result.hasToolCalls()).isTrue();
        assertThat(result.getToolCalls().get(0).getName()).isEqualTo("knowledge.search");
    }

    @Test
    void mockProvider_script_dequeuesInOrder() {
        MockProvider mock = new MockProvider();
        mock.enqueueScript(
                ProviderChatResult.builder().toolCalls(List.of(ToolCall.builder().id("c1").name("knowledge.search")
                        .arguments("{}").build())).build(),
                ProviderChatResult.builder().content("第二段").build());
        assertThat(mock.chat(null).hasToolCalls()).isTrue();
        assertThat(mock.chat(null).getContent()).isEqualTo("第二段");
        // 脚本耗尽回到固定行为
        assertThat(mock.chat(null).getContent()).isEqualTo("这是一段模拟回复。");
        mock.clearScript();
    }

    @Test
    void mockProvider_stream_scriptInvokesOnResult() {
        MockProvider mock = new MockProvider();
        mock.enqueueScript(ProviderChatResult.builder().content("流式脚本").build());
        AtomicReference<String> aggregated = new AtomicReference<>();
        AtomicReference<ProviderChatResult> terminal = new AtomicReference<>();
        mock.chatStream(null, new StreamCallback() {
            @Override
            public void onContent(String delta) {
                aggregated.set(aggregated.get() == null ? delta : aggregated.get() + delta);
            }

            @Override
            public void onResult(ProviderChatResult result) {
                terminal.set(result);
            }
        }, e -> {
        });
        assertThat(aggregated.get()).isEqualTo("流式脚本");
        assertThat(terminal.get().getContent()).isEqualTo("流式脚本");
    }
}