package com.calwen.xlumen.ai.service.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * knowledge.search 工具单测：权限边界只以可见库集合为准（不附加调用者 workspace 过滤），
 * 覆盖可见库全集 / 会话锁定库 / 单篇知识级问答 / 越权 kbId 四种场景。
 *
 * @author calwen
 * @date 2026/9/14
 */
class KnowledgeSearchToolTest {

    /** 本空间可见库。 */
    private static final long OWN_KB = 10L;
    /** 它空间公开库（resolveVisibleKbIds 已纳入可见集合）。 */
    private static final long PUBLIC_KB = 20L;

    private KnowledgeApi knowledgeApi;
    private KnowledgeSearchTool tool;

    @BeforeEach
    void setUp() {
        knowledgeApi = mock(KnowledgeApi.class);
        tool = new KnowledgeSearchTool(knowledgeApi);
        when(knowledgeApi.resolveVisibleKbIds(anyLong())).thenReturn(List.of(OWN_KB, PUBLIC_KB));
        when(knowledgeApi.search(any(SearchRequestDTO.class))).thenReturn(List.of());
    }

    private SearchRequestDTO captureSearch() {
        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(knowledgeApi).search(captor.capture());
        return captor.getValue();
    }

    private static AgentToolContext context(Long kbId, List<Long> knowledgeIds) {
        return AgentToolContext.builder()
                .workspaceId(1L)
                .userId(100L)
                .kbId(kbId)
                .knowledgeIds(knowledgeIds)
                .build();
    }

    @Test
    void noKbId_searchesAllVisibleKbsWithoutWorkspaceClause() {
        JSONObject args = JSONUtil.createObj().set("query", "Redis 防重").set("topK", 5);

        String envelope = tool.execute(context(null, null), args);

        assertThat(ToolEventPayload.isOk(envelope)).isTrue();
        SearchRequestDTO request = captureSearch();
        // 跨空间公开知识可被召回：过滤只保留可见库集合，不附加调用者 workspace
        assertThat(request.getWorkspaceId()).isNull();
        assertThat(request.getKbIds()).containsExactly(OWN_KB, PUBLIC_KB);
    }

    @Test
    void sessionLockedPublicKb_restrictsToThatKb() {
        JSONObject args = JSONUtil.createObj().set("query", "Seata");

        String envelope = tool.execute(context(PUBLIC_KB, null), args);

        assertThat(ToolEventPayload.isOk(envelope)).isTrue();
        SearchRequestDTO request = captureSearch();
        assertThat(request.getKbIds()).containsExactly(PUBLIC_KB);
        assertThat(request.getWorkspaceId()).isNull();
    }

    @Test
    void explicitKbIdArg_restrictsToThatKb() {
        JSONObject args = JSONUtil.createObj().set("query", "Seata").set("kbId", String.valueOf(PUBLIC_KB));

        tool.execute(context(null, null), args);

        SearchRequestDTO request = captureSearch();
        assertThat(request.getKbIds()).containsExactly(PUBLIC_KB);
        assertThat(request.getWorkspaceId()).isNull();
    }

    @Test
    void singleKnowledgeQuestion_keepsKnowledgeFilterAndVisibleKbs() {
        // 详情页「问这篇 AI」：它空间公开知识 → 可见集合含其库 + 单篇限定
        long articleId = 2096971279868362778L;
        JSONObject args = JSONUtil.createObj().set("query", "这篇讲了什么");

        String envelope = tool.execute(context(null, List.of(articleId)), args);

        assertThat(ToolEventPayload.isOk(envelope)).isTrue();
        SearchRequestDTO request = captureSearch();
        assertThat(request.getKnowledgeId()).isEqualTo(articleId);
        assertThat(request.getWorkspaceId()).isNull();
        assertThat(request.getKbIds()).containsExactly(OWN_KB, PUBLIC_KB);
    }

    @Test
    void kbIdOutsideVisibleSet_rejected() {
        JSONObject args = JSONUtil.createObj().set("query", "x").set("kbId", "999");

        String envelope = tool.execute(context(null, null), args);

        assertThat(ToolEventPayload.isOk(envelope)).isFalse();
        assertThat(envelope).contains("无权访问该知识库");
        verify(knowledgeApi, never()).search(any(SearchRequestDTO.class));
    }

    @Test
    void lockedKbOutsideVisibleSet_rejected() {
        JSONObject args = JSONUtil.createObj().set("query", "x");

        String envelope = tool.execute(context(999L, null), args);

        assertThat(ToolEventPayload.isOk(envelope)).isFalse();
        assertThat(envelope).contains("无权访问该知识库");
        verify(knowledgeApi, never()).search(any(SearchRequestDTO.class));
    }
}
