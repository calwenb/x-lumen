package com.calwen.xlumen.ai.service.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * knowledge.list 工具（IDEA-025 F-0708）：列出当前用户可见知识库（id/名称/可见性），
 * 供模型先看有哪些库再决定检索范围。只读包装 KnowledgeApi（resolveVisibleKbIds + getKnowledgeBaseById）。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class KnowledgeListTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeListTool.class);

    private final KnowledgeApi knowledgeApi;

    public KnowledgeListTool(KnowledgeApi knowledgeApi) {
        this.knowledgeApi = knowledgeApi;
    }

    @Override
    public String name() {
        return "knowledge.list";
    }

    @Override
    public String description() {
        return "列出当前用户可见的知识库（id、名称、可见性 PUBLIC/PRIVATE），供决定检索范围；不检索内容。";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override
    public Set<AiScene> scenes() {
        return Set.of(AiScene.QA, AiScene.REVIEWER);
    }

    @Override
    public String execute(AgentToolContext ctx, JSONObject args) {
        try {
            List<Long> visible = knowledgeApi.resolveVisibleKbIds(ctx.getUserId());
            JSONArray data = new JSONArray();
            if (visible != null) {
                for (Long kbId : visible) {
                    KnowledgeBaseVO kb = knowledgeApi.getKnowledgeBaseById(kbId);
                    if (kb == null) {
                        continue;
                    }
                    data.add(JSONUtil.createObj()
                            .set("id", String.valueOf(kb.getId()))
                            .set("name", kb.getName() == null ? "" : kb.getName())
                            .set("visibility", Integer.valueOf(1).equals(kb.getVisibility()) ? "PUBLIC" : "PRIVATE"));
                }
            }
            return ToolEventPayload.okEnvelope(data);
        } catch (Exception e) {
            log.warn("knowledge.list 执行失败", e);
            return ToolEventPayload.errorEnvelope("列知识库失败：" + e.getClass().getSimpleName());
        }
    }
}