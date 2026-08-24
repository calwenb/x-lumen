package com.calwen.xlumen.ai.service.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.config.AiProperties;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.provider.ToolCall;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具注册表与三个工具单测（IDEA-025 F-0708）：越权 kbId 拦截、未知工具白名单、
 * 访客仅公开库、命中结果上报 citationCollector、结果截断。
 *
 * @author calwen
 * @date 2026/8/24
 */
class ToolRegistryTest {

    @Mock
    private KnowledgeApi knowledgeApi;

    private AiProperties aiProperties;
    private ToolRegistry registry;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiProperties = new AiProperties();
        aiProperties.setAgentToolTimeoutMillis(1000);
        aiProperties.setAgentToolResultMaxChars(200);
        registry = new ToolRegistry(
                List.of(new KnowledgeSearchTool(knowledgeApi), new KnowledgeListTool(knowledgeApi),
                        new KnowledgeDirectoryTool(knowledgeApi)),
                aiProperties);
        ctx = ToolContext.builder().workspaceId(1L).userId(100L).build();
    }

    @Test
    void unknownToolName_returnsErrorEnvelope() {
        String result = registry.execute(ctx, ToolCall.builder().name("hack.tool").arguments("{}").build());
        assertThat(result).contains("\"ok\":false").contains("未知工具");
    }

    @Test
    void search_unauthorizedKb_returnsErrorEnvelopeWithoutSearch() {
        when(knowledgeApi.resolveVisibleKbIds(100L)).thenReturn(List.of(1L));
        String result = registry.execute(ctx, ToolCall.builder().name("knowledge.search")
                .arguments("{\"query\":\"部署\",\"kbId\":\"999\"}").build());
        assertThat(result).contains("\"ok\":false").contains("无权访问该知识库");
        verify(knowledgeApi, never()).search(any());
    }

    @Test
    void search_visitorOnlyVisiblePublicKb() {
        when(knowledgeApi.resolveVisibleKbIds(null)).thenReturn(List.of(1L));
        when(knowledgeApi.search(any())).thenReturn(List.of(result(1L, "部署指南")));
        ToolContext visitor = ToolContext.builder().workspaceId(1L).userId(null).build();

        String out = registry.execute(visitor, ToolCall.builder().name("knowledge.search")
                .arguments("{\"query\":\"部署\"}").build());

        assertThat(out).contains("\"ok\":true").contains("部署指南");
        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(knowledgeApi).search(captor.capture());
        assertThat(captor.getValue().getKbIds()).containsExactly(1L);
    }

    @Test
    void search_hitsReportCitationCollector() {
        when(knowledgeApi.resolveVisibleKbIds(100L)).thenReturn(List.of(1L, 2L));
        when(knowledgeApi.search(any())).thenReturn(List.of(result(1L, "A"), result(2L, "B")));
        List<SearchResultDTO> collected = new java.util.ArrayList<>();
        ToolContext withCollector = ToolContext.builder().workspaceId(1L).userId(100L)
                .citationCollector(collected::addAll).build();

        String out = registry.execute(withCollector, ToolCall.builder().name("knowledge.search")
                .arguments("{\"query\":\"部署\",\"topK\":3}").build());

        assertThat(out).contains("\"ok\":true");
        assertThat(collected).hasSize(2);
        assertThat(collected.get(0).getTitle()).isEqualTo("A");
    }

    @Test
    void search_lockedKb_limitsToLockedKb() {
        when(knowledgeApi.resolveVisibleKbIds(100L)).thenReturn(List.of(1L, 2L));
        when(knowledgeApi.search(any())).thenReturn(List.of(result(1L, "A")));
        ToolContext locked = ToolContext.builder().workspaceId(1L).userId(100L).kbId(1L).build();

        registry.execute(locked, ToolCall.builder().name("knowledge.search").arguments("{\"query\":\"部署\"}").build());

        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(knowledgeApi).search(captor.capture());
        assertThat(captor.getValue().getKbIds()).containsExactly(1L);
    }

    @Test
    void list_returnsVisibleKbs() {
        when(knowledgeApi.resolveVisibleKbIds(100L)).thenReturn(List.of(1L));
        when(knowledgeApi.getKnowledgeBaseById(1L)).thenReturn(KnowledgeBaseVO.builder().id(1L).name("我的库").visibility(0).build());

        String out = registry.execute(ctx, ToolCall.builder().name("knowledge.list").arguments("{}").build());

        assertThat(out).contains("\"ok\":true").contains("我的库").contains("PRIVATE");
    }

    @Test
    void directory_unauthorizedKb_returnsErrorEnvelope() {
        when(knowledgeApi.resolveVisibleKbIds(100L)).thenReturn(List.of(1L));
        String out = registry.execute(ctx, ToolCall.builder().name("knowledge.getDirectoryTree")
                .arguments("{\"kbId\":\"8\"}").build());
        assertThat(out).contains("\"ok\":false").contains("无权访问");
    }

    @Test
    void resultTooLong_truncatedWithFlag() {
        // 用固定大输出的临时工具触发截断（KnowledgeSearchTool 单条 400 字上限，改走 registry 截断）
        AgentTool longTool = new AgentTool() {
            @Override
            public String name() {
                return "test.long";
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public String parametersSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public Set<AiScene> scenes() {
                return Set.of(AiScene.QA);
            }

            @Override
            public String execute(ToolContext c, JSONObject args) {
                return "{\"ok\":true,\"data\":[\"" + "x".repeat(1000) + "\"]}";
            }
        };
        ToolRegistry small = new ToolRegistry(List.of(longTool), aiProperties);
        String out = small.execute(ctx, ToolCall.builder().name("test.long").arguments("{}").build());
        assertThat(out).contains("truncated").hasSizeLessThan(300);
        assertThat(out).contains("\"ok\":true");
    }

    @Test
    void specsFor_sceneFiltersTools() {
        List<com.calwen.xlumen.ai.service.provider.ToolSpec> specs = registry.specsFor(AiScene.QA);
        assertThat(specs).extracting(com.calwen.xlumen.ai.service.provider.ToolSpec::getName)
                .containsExactlyInAnyOrder("knowledge.search", "knowledge.list", "knowledge.getDirectoryTree");
        // 其余场景本次无工具
        assertThat(registry.specsFor(AiScene.SUMMARY)).isEmpty();
    }

    private SearchResultDTO result(long id, String title) {
        return SearchResultDTO.builder().knowledgeId(id).title(title).chunkSeq(1).chunkText("内容").score(0.9f).build();
    }
}