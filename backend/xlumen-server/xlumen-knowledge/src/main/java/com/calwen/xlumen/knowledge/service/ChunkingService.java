package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.knowledge.dto.Chunk;

import java.util.List;

/**
 * 切片服务（F-0402）：Markdown 正文按标题边界切片，每片约 400~500 字并带 15% 重叠。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ChunkingService {

    /**
     * 将 Markdown 正文切片为 Chunk 列表（seq 从 1 递增，headingAnchor 为当前段落标题）。
     *
     * @param markdown Markdown 正文
     * @return 切片列表（正文为空返回空列表）
     */
    List<Chunk> chunk(String markdown);
}
