package com.calwen.xlumen.ai.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.ChatMemoryEntity;
import com.calwen.xlumen.ai.mapper.ChatMemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 对话长期记忆服务单测：记忆条目拼装、20 条截断丢最旧、访客跳过。
 *
 * @author calwen
 * @date 2026/8/26
 */
class ChatMemoryServiceImplTest {

    private ChatMemoryMapper mapper;
    private ChatMemoryServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ChatMemoryMapper.class);
        service = new ChatMemoryServiceImpl(mapper);
    }

    @Test
    void appendMemory_firstTime_insertsFormattedEntry() {
        when(mapper.selectOne(any())).thenReturn(null);
        service.appendMemory(1L, 1L, "如何配置数据库连接", "第一步安装驱动，第二步设置连接池。");

        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(mapper).insert(captor.capture());
        verify(mapper, never()).updateById(any(ChatMemoryEntity.class));

        String json = captor.getValue().getMemoryJson();
        assertThat(json).startsWith("[\"").endsWith("\"]");
        assertThat(json).contains("如何配置数据库连接：第一步安装驱动，第二步设置连接池。");
    }

    @Test
    void appendMemory_existing_capsAtTwentyDropsOldest() {
        ChatMemoryEntity existing = new ChatMemoryEntity();
        existing.setId(9L);
        List<String> base = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            base.add("2026-01-01 00:00 旧主题" + i);
        }
        existing.setMemoryJson(JSONUtil.toJsonStr(base));
        when(mapper.selectOne(any())).thenReturn(existing);

        service.appendMemory(1L, 1L, "新问题", "新回答");

        ArgumentCaptor<ChatMemoryEntity> captor = ArgumentCaptor.forClass(ChatMemoryEntity.class);
        verify(mapper).updateById(captor.capture());

        JSONArray arr = JSONUtil.parseArray(captor.getValue().getMemoryJson());
        assertThat(arr).hasSize(20);
        assertThat(arr.getStr(0)).contains("旧主题2");
        assertThat(arr.getStr(19)).contains("新问题：新回答");
    }

    @Test
    void appendMemory_nullUser_skipsWithoutAnyIo() {
        service.appendMemory(1L, null, "问题", "回答");
        verifyNoInteractions(mapper);
    }

    @Test
    void loadMemory_nullUser_returnsNull() {
        assertThat(service.loadMemory(1L, null)).isNull();
        verifyNoInteractions(mapper);
    }

    @Test
    void loadMemory_withMemories_joinsLines() {
        ChatMemoryEntity entity = new ChatMemoryEntity();
        entity.setMemoryJson(JSONUtil.toJsonStr(List.of("2026-08-01 10:00 主题甲：要点甲", "2026-08-02 11:00 主题乙：要点乙")));
        when(mapper.selectOne(any())).thenReturn(entity);

        String memory = service.loadMemory(1L, 1L);
        assertThat(memory).isEqualTo("2026-08-01 10:00 主题甲：要点甲\n2026-08-02 11:00 主题乙：要点乙");
    }

    @Test
    void loadMemory_datastoreError_returnsNull() {
        when(mapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        assertThat(service.loadMemory(1L, 1L)).isNull();
    }
}