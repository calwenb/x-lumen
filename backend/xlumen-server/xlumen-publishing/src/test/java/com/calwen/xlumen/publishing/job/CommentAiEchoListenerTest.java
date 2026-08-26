package com.calwen.xlumen.publishing.job;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.publishing.entity.CommentEntity;
import com.calwen.xlumen.publishing.event.CommentAiEchoRequestedEvent;
import com.calwen.xlumen.publishing.mapper.CommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 小光评论回复监听单元测试：命中片段拼接组文、检索为空兜底文案、
 * 引用溯源 JSON 落库、限流静默跳过与生成失败不抛出（不影响评论主流程）。
 *
 * @author calwen
 * @date 2026/8/26
 */
class CommentAiEchoListenerTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private KnowledgeApi knowledgeApi;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CommentAiEchoListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new CommentAiEchoListener(commentMapper, knowledgeApi, stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(knowledgeApi.resolveVisibleKbIds(eq(1L))).thenReturn(List.of(10L));
    }

    @Test
    void onAiEchoRequested_withHits_writesAiReplyWithCitations() {
        when(knowledgeApi.search(any(SearchRequestDTO.class))).thenReturn(List.of(
                hit(200L, "部署指南", "## 快速开始", "第一步安装依赖", 0.91f),
                hit(200L, "部署指南", "## 配置项", "第二步配置环境变量", 0.83f)));

        listener.onAiEchoRequested(event());

        ArgumentCaptor<SearchRequestDTO> requestCaptor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(knowledgeApi).search(requestCaptor.capture());
        SearchRequestDTO request = requestCaptor.getValue();
        // 跨空间公开知识：workspaceId 为空，可见性由 kbIds+article_id 表达
        assertThat(request.getWorkspaceId()).isNull();
        assertThat(request.getKnowledgeId()).isEqualTo(200L);
        assertThat(request.getQuery()).isEqualTo("这篇文章写得很好 @小光");
        assertThat(request.getKbIds()).containsExactly(10L);
        assertThat(request.getKnowledgeId()).isEqualTo(200L);
        assertThat(request.getTopK()).isEqualTo(5);

        CommentEntity reply = capturedInsert();
        assertThat(reply.getIsAi()).isEqualTo(1);
        assertThat(reply.getParentId()).isEqualTo(900L);
        assertThat(reply.getUserId()).isZero();
        assertThat(reply.getUserName()).isEqualTo("小光");
        assertThat(reply.getKnowledgeId()).isEqualTo(200L);
        assertThat(reply.getStatus()).isEqualTo(1);
        assertThat(reply.getContent()).isEqualTo(
                "基于知识库的回答：\n\n第一步安装依赖\n\n第二步配置环境变量");
        JSONArray citations = JSONUtil.parseArray(reply.getCitationsJson());
        assertThat(citations).hasSize(2);
        assertThat(citations.getJSONObject(0).getLong("knowledgeId")).isEqualTo(200L);
        assertThat(citations.getJSONObject(0).getStr("title")).isEqualTo("部署指南");
        assertThat(citations.getJSONObject(0).getStr("headingAnchor")).isEqualTo("## 快速开始");
        assertThat(citations.getJSONObject(0).getStr("chunkText")).isEqualTo("第一步安装依赖");
        assertThat(citations.getJSONObject(0).getFloat("score")).isEqualTo(0.91f);
    }

    @Test
    void onAiEchoRequested_withoutHits_writesFallbackReply() {
        when(knowledgeApi.search(any(SearchRequestDTO.class))).thenReturn(List.of());

        listener.onAiEchoRequested(event());

        CommentEntity reply = capturedInsert();
        assertThat(reply.getIsAi()).isEqualTo(1);
        assertThat(reply.getParentId()).isEqualTo(900L);
        assertThat(reply.getContent()).isEqualTo("库内暂时没有找到相关内容，换个问法试试");
        assertThat(reply.getCitationsJson()).isEqualTo("[]");
    }

    @Test
    void onAiEchoRequested_withoutVisibleKb_writesFallbackReply() {
        when(knowledgeApi.resolveVisibleKbIds(eq(1L))).thenReturn(List.of());

        listener.onAiEchoRequested(event());

        verify(knowledgeApi, never()).search(any(SearchRequestDTO.class));
        CommentEntity reply = capturedInsert();
        assertThat(reply.getContent()).isEqualTo("库内暂时没有找到相关内容，换个问法试试");
    }

    @Test
    void onAiEchoRequested_rateLimited_skipsInsertSilently() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        listener.onAiEchoRequested(event());

        verify(knowledgeApi, never()).search(any(SearchRequestDTO.class));
        verify(commentMapper, never()).insert(any(CommentEntity.class));
    }

    @Test
    void onAiEchoRequested_searchFailure_doesNotThrowAndSkipsInsert() {
        when(knowledgeApi.search(any(SearchRequestDTO.class)))
                .thenThrow(new IllegalStateException("检索暂不可用"));

        assertThatCode(() -> listener.onAiEchoRequested(event())).doesNotThrowAnyException();

        verify(commentMapper, never()).insert(any(CommentEntity.class));
    }

    @Test
    void onAiEchoRequested_nullEvent_skips() {
        assertThatCode(() -> listener.onAiEchoRequested(null)).doesNotThrowAnyException();
        verify(commentMapper, never()).insert(any(CommentEntity.class));
    }

    private CommentEntity capturedInsert() {
        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentMapper).insert(captor.capture());
        return captor.getValue();
    }

    private CommentAiEchoRequestedEvent event() {
        return CommentAiEchoRequestedEvent.builder()
                .workspaceId(100L)
                .knowledgeId(200L)
                .userId(1L)
                .knowledgeTitle("部署指南")
                .knowledgeContent("正文快照")
                .commentId(900L)
                .commentContent("这篇文章写得很好 @小光")
                .username("tester")
                .build();
    }

    private SearchResultDTO hit(Long knowledgeId, String title, String anchor, String text, float score) {
        return SearchResultDTO.builder()
                .knowledgeId(knowledgeId).title(title).headingAnchor(anchor)
                .chunkText(text).score(score).build();
    }
}