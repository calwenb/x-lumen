package com.calwen.xlumen.ai.service.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * knowledge.search 工具：包装 KnowledgeApi.search（只读，红线合规）。
 * 权限规则：kbId 必须在 resolveVisibleKbIds(userId) 集合内，否则错误信封；未传 kbId 按可见库全集检索（D13）；
 * 命中结果同时上报 citationCollector 聚合进引用事件。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class KnowledgeSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchTool.class);

    /** 单条结果 chunkText 截断长度（整包另有 agentToolResultMaxChars 截断）。 */
    private static final int CHUNK_CUT = 400;
    private static final int TOP_K_MIN = 1;
    private static final int TOP_K_MAX = 10;
    private static final int TOP_K_DEFAULT = 5;

    private final KnowledgeApi knowledgeApi;

    public KnowledgeSearchTool(KnowledgeApi knowledgeApi) {
        this.knowledgeApi = knowledgeApi;
    }

    @Override
    public String name() {
        return "knowledge.search";
    }

    @Override
    public String description() {
        return "检索知识库，返回与查询最相关的知识片段。可限定单个知识库（kbId），topK 控制返回条数（1~10，默认 5）；"
                + "不传 kbId 时检索当前用户全部可见库。引用检索到的原文时用 [n] 标注编号。";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\",\"description\":\"检索关键词或问题\"},"
                + "\"kbId\":{\"type\":\"string\",\"description\":\"知识库 ID（可空，缺省检索全部可见库）\"},"
                + "\"topK\":{\"type\":\"integer\",\"description\":\"返回条数 1~10，默认 5\"}"
                + "},\"required\":[\"query\"]}";
    }

    @Override
    public Set<AiScene> scenes() {
        return Set.of(AiScene.QA, AiScene.REVIEWER);
    }

    @Override
    public String execute(AgentToolContext ctx, JSONObject args) {
        try {
            String query = args.getStr("query");
            if (StrUtil.isBlank(query)) {
                return ToolEventPayload.errorEnvelope("knowledge.search 缺少必填参数 query");
            }
            int topK = clampTopK(args.getInt("topK", TOP_K_DEFAULT));
            List<Long> visible = knowledgeApi.resolveVisibleKbIds(ctx.getUserId());
            List<Long> kbIds;
            String kbIdStr = args.getStr("kbId");
            if (StrUtil.isNotBlank(kbIdStr)) {
                Long kbId = parseId(kbIdStr);
                if (kbId == null || visible == null || !visible.contains(kbId)) {
                    return ToolEventPayload.errorEnvelope("无权访问该知识库（kbId=" + kbIdStr + "）");
                }
                kbIds = List.of(kbId);
            } else if (ctx.getKbId() != null) {
                // 会话锁定知识库：未显式指定时默认限定锁定的库
                if (visible == null || !visible.contains(ctx.getKbId())) {
                    return ToolEventPayload.errorEnvelope("无权访问该知识库（kbId=" + ctx.getKbId() + "）");
                }
                kbIds = List.of(ctx.getKbId());
            } else {
                kbIds = visible == null ? List.of() : visible;
            }
            List<SearchResultDTO> results;
            SearchRequestDTO.SearchRequestDTOBuilder searchBuilder = SearchRequestDTO.builder()
                    .workspaceId(ctx.getWorkspaceId())
                    .query(query)
                    .kbIds(kbIds)
                    .topK(topK);
            List<Long> knowledgeIds = ctx.getKnowledgeIds();
            if (knowledgeIds != null && knowledgeIds.size() == 1) {
                // 单篇精确限定（多篇对比保持可见库检索，对比语义由请求方表达）
                searchBuilder.knowledgeId(knowledgeIds.get(0));
            }
            results = knowledgeApi.search(searchBuilder.build());
            JSONArray data = new JSONArray();
            for (SearchResultDTO r : results) {
                data.add(JSONUtil.createObj()
                        .set("knowledgeId", String.valueOf(r.getKnowledgeId()))
                        .set("title", r.getTitle() == null ? "" : r.getTitle())
                        .set("chunkSeq", r.getChunkSeq())
                        .set("headingAnchor", r.getHeadingAnchor() == null ? "" : r.getHeadingAnchor())
                        .set("chunkText", cut(r.getChunkText()))
                        .set("score", r.getScore()));
            }
            if (ctx.getCitationCollector() != null && !results.isEmpty()) {
                ctx.getCitationCollector().accept(results);
            }
            return ToolEventPayload.okEnvelope(data);
        } catch (Exception e) {
            log.warn("knowledge.search 执行失败", e);
            return ToolEventPayload.errorEnvelope("检索失败：" + e.getClass().getSimpleName());
        }
    }

    private int clampTopK(Integer topK) {
        if (topK == null) {
            return TOP_K_DEFAULT;
        }
        return Math.max(TOP_K_MIN, Math.min(TOP_K_MAX, topK));
    }

    private Long parseId(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String cut(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > CHUNK_CUT ? text.substring(0, CHUNK_CUT) + "…" : text;
    }
}