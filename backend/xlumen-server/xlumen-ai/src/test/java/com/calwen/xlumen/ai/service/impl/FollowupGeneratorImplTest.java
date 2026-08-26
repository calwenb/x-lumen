package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.ChatRuntime;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 相关追问生成服务单测：按行解析取前 3 条非空、生成失败返回空不抛出。
 *
 * @author calwen
 * @date 2026/8/26
 */
class FollowupGeneratorImplTest {

    private ChatRuntime chatRuntime;
    private FollowupGeneratorImpl generator;

    @BeforeEach
    void setUp() {
        chatRuntime = mock(ChatRuntime.class);
        generator = new FollowupGeneratorImpl(chatRuntime);
    }

    @Test
    void parse_skipsBlankLinesAndCapsAtThree() {
        String raw = "追问一\n\n  追问二  \n追问三\n追问四\n追问五";
        List<String> result = generator.parse(raw);
        assertThat(result).containsExactly("追问一", "追问二", "追问三");
    }

    @Test
    void parse_blankText_returnsEmpty() {
        assertThat(generator.parse(null)).isEmpty();
        assertThat(generator.parse("   \n \t ")).isEmpty();
    }

    @Test
    void generate_success_returnsParsedRows() {
        when(chatRuntime.chat(eq(1L), eq(AiScene.WRITING), anyList(), eq(0.5), eq(256)))
                .thenReturn("追问一\n追问二\n追问三");

        List<String> result = generator.generate(1L, "问题", "回答摘要");

        assertThat(result).containsExactly("追问一", "追问二", "追问三");
        verify(chatRuntime).chat(eq(1L), eq(AiScene.WRITING), anyList(), eq(0.5), eq(256));
    }

    @Test
    void generate_runtimeFailure_returnsEmpty() {
        when(chatRuntime.chat(any(), any(), anyList(), any(), any()))
                .thenThrow(new BizException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂时不可用"));
        assertThat(generator.generate(1L, "问题", "回答摘要")).isEmpty();
    }
}