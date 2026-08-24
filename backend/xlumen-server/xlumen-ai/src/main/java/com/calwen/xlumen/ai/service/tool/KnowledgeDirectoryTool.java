package com.calwen.xlumen.ai.service.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * knowledge.getDirectoryTree 工具（IDEA-025 F-0708）：获取知识库目录树（id 序列化为字符串防精度丢失），
 * 供模型按目录缩小检索范围。只读包装 KnowledgeApi.getDirectoryTree，kbId 强制校验可见集合。
 *
 * @author calwen
 * @date 2026/8/24
 */
@Component
public class KnowledgeDirectoryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDirectoryTool.class);

    private final KnowledgeApi knowledgeApi;

    public KnowledgeDirectoryTool(KnowledgeApi knowledgeApi) {
        this.knowledgeApi = knowledgeApi;
    }

    @Override
    public String name() {
        return "knowledge.getDirectoryTree";
    }

    @Override
    public String description() {
        return "获取指定知识库的目录树（多级目录），供按目录缩小检索范围。需要先通过 knowledge.list 得知可用知识库。";
    }

    @Override
    public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"kbId\":{\"type\":\"string\",\"description\":\"知识库 ID（必填）\"}"
                + "},\"required\":[\"kbId\"]}";
    }

    @Override
    public Set<AiScene> scenes() {
        return Set.of(AiScene.QA, AiScene.REVIEWER);
    }

    @Override
    public String execute(AgentToolContext ctx, JSONObject args) {
        try {
            String kbIdStr = args.getStr("kbId");
            if (StrUtil.isBlank(kbIdStr)) {
                return ToolEventPayload.errorEnvelope("knowledge.getDirectoryTree 缺少必填参数 kbId");
            }
            Long kbId = parseId(kbIdStr);
            List<Long> visible = knowledgeApi.resolveVisibleKbIds(ctx.getUserId());
            if (kbId == null || visible == null || !visible.contains(kbId)) {
                return ToolEventPayload.errorEnvelope("无权访问该知识库（kbId=" + kbIdStr + "）");
            }
            List<DirectoryVO> tree = knowledgeApi.getDirectoryTree(kbId);
            JSONArray data = new JSONArray();
            for (DirectoryVO node : tree) {
                data.add(toJson(node));
            }
            return ToolEventPayload.okEnvelope(data);
        } catch (Exception e) {
            log.warn("knowledge.getDirectoryTree 执行失败", e);
            return ToolEventPayload.errorEnvelope("获取目录树失败：" + e.getClass().getSimpleName());
        }
    }

    private JSONObject toJson(DirectoryVO node) {
        JSONObject o = JSONUtil.createObj()
                .set("id", String.valueOf(node.getId()))
                .set("parentId", String.valueOf(node.getParentId()))
                .set("name", node.getName() == null ? "" : node.getName());
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            JSONArray children = new JSONArray();
            for (DirectoryVO c : node.getChildren()) {
                children.add(toJson(c));
            }
            o.set("children", children);
        }
        return o;
    }

    private Long parseId(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}