package com.calwen.xlumen.publishing.service.impl;

import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.CreateFeedbackDTO;
import com.calwen.xlumen.publishing.entity.FeedbackEntity;
import com.calwen.xlumen.publishing.mapper.FeedbackMapper;
import com.calwen.xlumen.publishing.vo.FeedbackVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 读者纠错服务单元测试：跨空间公开知识可提交（落知识归属空间）、
 * 存在性/可见性校验失败不消耗 IP 限流额度、超限 429。
 *
 * @author calwen
 * @date 2026/9/14
 */
class FeedbackServiceImplTest {

    @Mock
    private FeedbackMapper feedbackMapper;
    @Mock
    private ContentApi contentApi;
    @Mock
    private KnowledgeApi knowledgeApi;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createFeedback_publicKnowledgeInNonDefaultWorkspace_successAndOwnWorkspaceStored() {
        // 知识归属非默认空间（999 库 / 555 空间），纠错记录须落知识自身空间而非默认空间
        when(knowledgeApi.resolveVisibleKbIds(null)).thenReturn(List.of(999L));
        when(contentApi.getPublished(null, 100L, List.of(999L)))
                .thenReturn(KnowledgeDetailDTO.builder().id(100L).kbId(999L).title("公开文章").build());
        when(knowledgeApi.getKnowledgeBaseById(999L))
                .thenReturn(KnowledgeBaseVO.builder().id(999L).workspaceId(555L).visibility(1).build());
        when(valueOperations.increment(anyString())).thenReturn(1L);

        FeedbackVO vo = feedbackService.createFeedback(100L,
                CreateFeedbackDTO.builder().problem("正文有误").ip("1.2.3.4").build());

        assertThat(vo.getTrackNo()).isNotBlank();
        ArgumentCaptor<FeedbackEntity> captor = ArgumentCaptor.forClass(FeedbackEntity.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo(555L);
        assertThat(captor.getValue().getKnowledgeId()).isEqualTo(100L);
    }

    @Test
    void createFeedback_knowledgeNotVisible_doesNotConsumeRateLimit() {
        when(knowledgeApi.resolveVisibleKbIds(null)).thenReturn(List.of(999L));
        when(contentApi.getPublished(null, 100L, List.of(999L))).thenReturn(null);

        assertThatThrownBy(() -> feedbackService.createFeedback(100L,
                CreateFeedbackDTO.builder().problem("正文有误").ip("1.2.3.4").build()))
                .isInstanceOf(BizException.class)
                .hasMessage("知识不存在");

        // 校验失败：不得占用该 IP 的每分钟额度，也不落库
        verify(valueOperations, never()).increment(anyString());
        verify(feedbackMapper, never()).insert(any(FeedbackEntity.class));
    }

    @Test
    void createFeedback_sameIpSecondSubmission_rateLimited() {
        when(knowledgeApi.resolveVisibleKbIds(null)).thenReturn(List.of(999L));
        when(contentApi.getPublished(null, 100L, List.of(999L)))
                .thenReturn(KnowledgeDetailDTO.builder().id(100L).kbId(999L).build());
        when(knowledgeApi.getKnowledgeBaseById(999L))
                .thenReturn(KnowledgeBaseVO.builder().id(999L).workspaceId(555L).visibility(1).build());
        // 同 IP 第二次：原子递增返回 2，超限 429
        when(valueOperations.increment(anyString())).thenReturn(2L);

        assertThatThrownBy(() -> feedbackService.createFeedback(100L,
                CreateFeedbackDTO.builder().problem("再次提交").ip("1.2.3.4").build()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS))
                .hasMessage("提交过于频繁，请稍后再试");
        verify(feedbackMapper, never()).insert(any(FeedbackEntity.class));
    }
}
