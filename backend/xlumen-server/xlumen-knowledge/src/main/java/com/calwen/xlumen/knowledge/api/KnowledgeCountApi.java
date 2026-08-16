package com.calwen.xlumen.knowledge.api;

import java.util.Collection;
import java.util.Map;

/**
 * 知识数统计提供方（反向 SPI，决策 D15/D16）：接口定义在 knowledge 模块，实现由 content 模块提供
 * （cnt_knowledge 属 content，依赖方向 content→knowledge 下 knowledge 无法直查内容表）。
 * 知识库/目录列表的 knowledgeCount 由本接口聚合补全（F-0308/F-0309 展示），无实现时回退 0。
 *
 * @author calwen
 * @date 2026/8/16
 */
public interface KnowledgeCountApi {

    /** 统计一批知识库下的非回收站知识数（键为 kbId，缺省 0）。 */
    Map<Long, Long> countByKbIds(Long workspaceId, Collection<Long> kbIds);

    /** 统计指定库下一批目录（不含库根 0）的非回收站知识数（键为 directoryId，缺省 0）。 */
    Map<Long, Long> countByDirectoryIds(Long workspaceId, Long kbId, Collection<Long> directoryIds);
}
