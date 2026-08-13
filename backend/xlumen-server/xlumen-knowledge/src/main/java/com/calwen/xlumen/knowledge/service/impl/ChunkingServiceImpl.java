package com.calwen.xlumen.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.knowledge.dto.Chunk;
import com.calwen.xlumen.knowledge.service.ChunkingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 切片服务实现（F-0402）：按 Markdown 标题（#/##/###）边界切段，段内超过上限再以
 * 滑动窗口细分，相邻切片带 15% 重叠，保证语义边界不割裂。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class ChunkingServiceImpl implements ChunkingService {

    /** 目标切片字数。 */
    private static final int CHUNK_TARGET = 450;
    /** 单切片字数上限（超过即细分）。 */
    private static final int CHUNK_MAX = 500;
    /** 相邻切片重叠比例。 */
    private static final double OVERLAP_RATIO = 0.15;
    /** 标题匹配：#/##/### 行首标题（4 级及以上不作为边界）。 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^(#{1,3})\\s+(.+)$");

    @Override
    public List<Chunk> chunk(String markdown) {
        if (StrUtil.isBlank(markdown)) {
            return List.of();
        }
        List<Chunk> chunks = new ArrayList<>();
        int seq = 1;
        for (Section section : splitSections(markdown)) {
            String body = StrUtil.trim(section.body);
            if (StrUtil.isBlank(body)) {
                continue;
            }
            if (body.length() <= CHUNK_MAX) {
                chunks.add(Chunk.builder().seq(seq++).headingAnchor(section.heading).chunkText(body).build());
                continue;
            }
            // 滑动窗口细分：步长 = 目标长度 - 重叠长度，保证相邻切片 15% 重叠
            int overlap = (int) Math.round(CHUNK_TARGET * OVERLAP_RATIO);
            int step = CHUNK_TARGET - overlap;
            for (int start = 0; start < body.length(); start += step) {
                int end = Math.min(body.length(), start + CHUNK_TARGET);
                chunks.add(Chunk.builder().seq(seq++).headingAnchor(section.heading)
                        .chunkText(body.substring(start, end)).build());
                if (end >= body.length()) {
                    break;
                }
            }
        }
        return chunks;
    }

    /** 按标题边界将正文拆为若干段落，每段携带其归属的标题锚点。 */
    private List<Section> splitSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);
        int cursor = 0;
        String heading = "";
        while (matcher.find()) {
            sections.add(new Section(heading, markdown.substring(cursor, matcher.start())));
            heading = StrUtil.trim(matcher.group(2));
            cursor = matcher.end();
        }
        sections.add(new Section(heading, markdown.substring(cursor)));
        return sections;
    }

    /** 内部段落模型：标题锚点 + 段落正文。 */
    private static final class Section {
        private final String heading;
        private final String body;

        private Section(String heading, String body) {
            this.heading = heading;
            this.body = body;
        }
    }
}
