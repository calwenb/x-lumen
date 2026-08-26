package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.SceneConfigService;
import com.calwen.xlumen.ai.service.SceneModel;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配额服务单测：预占-结算-释放、超限 429、Redis 异常 fail-open。
 *
 * @author calwen
 * @date 2026/8/26
 */
class QuotaServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SceneConfigService sceneConfigService;
    private QuotaServiceImpl quotaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        sceneConfigService = mock(SceneConfigService.class);
        when(sceneConfigService.resolve(anyLong(), any())).thenReturn(SceneModel.builder().build());
        quotaService = new QuotaServiceImpl(redisTemplate, sceneConfigService);
    }

    @Test
    void reserve_unlimitedQuota_skipsRedis() {
        quotaService.reserve(1L, AiScene.QA);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void reserve_withinQuota_incrementsAndSetsTtlOnFirst() {
        when(sceneConfigService.resolve(anyLong(), any()))
                .thenReturn(SceneModel.builder().dailyQuota(10).build());
        when(valueOps.increment(any(String.class))).thenReturn(1L);
        assertThatCode(() -> quotaService.reserve(1L, AiScene.QA)).doesNotThrowAnyException();
        verify(valueOps).increment(any(String.class));
        verify(redisTemplate).expire(any(String.class), any(java.time.Duration.class));
    }

    @Test
    void reserve_overQuota_throwsTooManyRequestsAndRollsBack() {
        when(sceneConfigService.resolve(anyLong(), any()))
                .thenReturn(SceneModel.builder().dailyQuota(2).build());
        when(valueOps.increment(any(String.class))).thenReturn(3L);
        assertThatThrownBy(() -> quotaService.reserve(1L, AiScene.QA))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        verify(valueOps).decrement(any(String.class));
    }

    @Test
    void reserve_redisDown_failsOpen() {
        when(sceneConfigService.resolve(anyLong(), any()))
                .thenReturn(SceneModel.builder().dailyQuota(5).build());
        when(valueOps.increment(any(String.class))).thenThrow(new RuntimeException("redis down"));
        assertThatCode(() -> quotaService.reserve(1L, AiScene.QA)).doesNotThrowAnyException();
    }

    @Test
    void release_decrementsAndDeletesWhenZero() {
        when(valueOps.decrement(any(String.class))).thenReturn(0L);
        quotaService.release(1L, AiScene.QA);
        verify(valueOps).decrement(any(String.class));
        verify(redisTemplate).delete(any(String.class));
    }
}