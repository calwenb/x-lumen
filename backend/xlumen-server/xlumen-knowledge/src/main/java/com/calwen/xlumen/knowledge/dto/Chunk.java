package com.calwen.xlumen.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 切片数据载体（模块内部类型，F-0402/F-0405）：切片服务输出、向量库写入输入。
 * embedding 在 Embedding 阶段回填，Noop 降级时保持 null（不写向量）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {

    /** 切片序号（从 1 开始）。 */
    private int seq;

    /** 段落标题锚点（Markdown 标题，引用溯源跳转原文）。 */
    private String headingAnchor;

    /** 切片文本。 */
    private String chunkText;

    /** 切片向量（Embedding 后填充，Noop 降级时为 null）。 */
    private List<Float> embedding;
}
