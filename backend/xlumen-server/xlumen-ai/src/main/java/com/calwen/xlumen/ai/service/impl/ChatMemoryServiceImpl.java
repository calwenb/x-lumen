package com.calwen.xlumen.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.calwen.xlumen.ai.entity.ChatMemoryEntity;
import com.calwen.xlumen.ai.mapper.ChatMemoryMapper;
import com.calwen.xlumen.ai.service.ChatMemoryService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话长期记忆实现：loadMemory 汇成多行文本供 System Prompt 注入；
 * appendMemory 拼「主题-要点」条目并追加写入 memory_json（JSON 数组文本，上限 20 条，超出丢最旧）。
 * 记忆写回异常一律吞掉，不阻断对话流。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryServiceImpl.class);

    /** 记忆条目上限（超出丢最旧）。 */
    private static final int MAX_ENTRIES = 20;
    /** 主题截断长度。 */
    private static final int TOPIC_MAX = 30;
    /** 要点截断长度（回答摘要）。 */
    private static final int POINT_MAX = 200;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatMemoryMapper chatMemoryMapper;

    public ChatMemoryServiceImpl(ChatMemoryMapper chatMemoryMapper) {
        this.chatMemoryMapper = chatMemoryMapper;
    }

    @Override
    public String loadMemory(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return null;
        }
        try {
            ChatMemoryEntity entity = selectOne(workspaceId, userId);
            if (entity == null || StrUtil.isBlank(entity.getMemoryJson())) {
                return null;
            }
            JSONArray arr = JSONUtil.parseArray(entity.getMemoryJson());
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            for (Object o : arr) {
                if (o != null && StrUtil.isNotBlank(String.valueOf(o))) {
                    lines.add(String.valueOf(o));
                }
            }
            return lines.isEmpty() ? null : String.join("\n", lines);
        } catch (Exception e) {
            log.warn("读取对话记忆失败 ws={} userId={}", workspaceId, userId, e);
            return null;
        }
    }

    @Override
    public void appendMemory(Long workspaceId, Long userId, String query, String answer) {
        if (workspaceId == null || userId == null) {
            return;
        }
        try {
            String entry = buildEntry(query, answer);
            List<String> entries = parseEntries(selectExisting(workspaceId, userId));
            entries.add(entry);
            if (entries.size() > MAX_ENTRIES) {
                entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES, entries.size()));
            }
            String memoryJson = JSONUtil.toJsonStr(entries);
            ChatMemoryEntity entity = selectOne(workspaceId, userId);
            if (entity == null) {
                ChatMemoryEntity created = new ChatMemoryEntity();
                created.setWorkspaceId(workspaceId);
                created.setUserId(userId);
                created.setMemoryJson(memoryJson);
                chatMemoryMapper.insert(created);
            } else {
                entity.setMemoryJson(memoryJson);
                chatMemoryMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("写回对话记忆失败 ws={} userId={}", workspaceId, userId, e);
        }
    }

    /** 拼一条记忆条目：`YYYY-MM-DD HH:mm 主题：要点`（主题取问题截断，要点取回答前 200 字）。 */
    private String buildEntry(String query, String answer) {
        String topic = cut(query == null ? "" : query.trim().replaceAll("\\s+", " "), TOPIC_MAX);
        String point = cut(answer == null ? "" : answer.trim().replaceAll("\\s+", " "), POINT_MAX);
        String base = LocalDateTime.now().format(FORMATTER) + " " + topic;
        return StrUtil.isBlank(point) ? base : base + "：" + point;
    }

    /** 解析记忆数组文本为条目列表；空/非法返回空列表。 */
    private List<String> parseEntries(String memoryJson) {
        List<String> entries = new ArrayList<>();
        if (StrUtil.isBlank(memoryJson)) {
            return entries;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(memoryJson);
            if (arr != null) {
                for (Object o : arr) {
                    if (o != null && StrUtil.isNotBlank(String.valueOf(o))) {
                        entries.add(String.valueOf(o));
                    }
                }
            }
        } catch (Exception ignored) {
            // 非法 JSON 按空列表处理，不阻断追加
        }
        return entries;
    }

    private String selectExisting(Long workspaceId, Long userId) {
        ChatMemoryEntity entity = selectOne(workspaceId, userId);
        return entity == null ? null : entity.getMemoryJson();
    }

    private ChatMemoryEntity selectOne(Long workspaceId, Long userId) {
        return chatMemoryMapper.selectOne(new LambdaQueryWrapper<ChatMemoryEntity>()
                .eq(ChatMemoryEntity::getWorkspaceId, workspaceId)
                .eq(ChatMemoryEntity::getUserId, userId)
                .last("LIMIT 1"));
    }

    private String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}