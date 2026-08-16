package com.calwen.xlumen.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.content.entity.KnowledgeEntity;
import com.calwen.xlumen.content.mapper.KnowledgeMapper;
import com.calwen.xlumen.knowledge.api.KnowledgeCountApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识数统计实现（反向 SPI）：cnt_knowledge 属 content 模块，按 kb_id/directory_id 聚合非回收站知识数，
 * 供 knowledge 模块的知识库/目录列表展示（F-0308/F-0309，KB-3 遗留的恒 0 技术债闭环）。
 *
 * @author calwen
 * @date 2026/8/16
 */
@Service
public class KnowledgeCountApiImpl implements KnowledgeCountApi {

    @Resource
    private KnowledgeMapper knowledgeMapper;

    @Override
    public Map<Long, Long> countByKbIds(Long workspaceId, Collection<Long> kbIds) {
        if (workspaceId == null || kbIds == null || kbIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = knowledgeMapper.selectMaps(Wrappers.<KnowledgeEntity>query()
                .select("kb_id AS kbId", "COUNT(*) AS cnt")
                .eq("workspace_id", workspaceId)
                .eq("recycle_status", 0)
                .in("kb_id", kbIds)
                .groupBy("kb_id"));
        return toCountMap(rows, "kbId");
    }

    @Override
    public Map<Long, Long> countByDirectoryIds(Long workspaceId, Long kbId, Collection<Long> directoryIds) {
        if (workspaceId == null || kbId == null || directoryIds == null || directoryIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = knowledgeMapper.selectMaps(Wrappers.<KnowledgeEntity>query()
                .select("directory_id AS directoryId", "COUNT(*) AS cnt")
                .eq("workspace_id", workspaceId)
                .eq("kb_id", kbId)
                .eq("recycle_status", 0)
                .in("directory_id", directoryIds)
                .groupBy("directory_id"));
        return toCountMap(rows, "directoryId");
    }

    /** 聚合行（{key 列: Long, cnt: Long}）→ Map，key 不区分大小写兼容各驱动。 */
    private Map<Long, Long> toCountMap(List<Map<String, Object>> rows, String keyCol) {
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) getInsensitive(row, keyCol)).longValue(),
                row -> ((Number) getInsensitive(row, "cnt")).longValue(),
                (a, b) -> a));
    }

    private Object getInsensitive(Map<String, Object> row, String name) {
        return row.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
