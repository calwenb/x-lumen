package com.calwen.xlumen.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.ai.api.AiApi;
import com.calwen.xlumen.ai.api.dto.SubmitTaskDTO;
import com.calwen.xlumen.ai.api.vo.TaskResultVO;
import com.calwen.xlumen.ai.entity.AiEnhanceResultEntity;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.mapper.AiEnhanceResultMapper;
import com.calwen.xlumen.ai.service.AiTaskService;
import com.calwen.xlumen.ai.service.EnhanceService;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 模块对外接口实现（BACKEND.md §5.2）：跨模块任务提交与查询
 * + 知识最新摘要查询（publishing 详情页填充）。
 * 调用方（content/publishing）通过 AiApi 提交写作/审校任务后轮询结果。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class AiApiImpl implements AiApi {

    private static final Logger log = LoggerFactory.getLogger(AiApiImpl.class);

    private final AiTaskService aiTaskService;
    private final AiEnhanceResultMapper enhanceResultMapper;
    private final EnhanceService enhanceService;

    public AiApiImpl(AiTaskService aiTaskService, AiEnhanceResultMapper enhanceResultMapper,
                     EnhanceService enhanceService) {
        this.aiTaskService = aiTaskService;
        this.enhanceResultMapper = enhanceResultMapper;
        this.enhanceService = enhanceService;
    }

    @Override
    public Long submitTask(SubmitTaskDTO dto) {
        AiScene scene;
        try {
            scene = AiScene.valueOf(dto.getScene());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "未知场景：" + dto.getScene());
        }
        return aiTaskService.submit(dto.getWorkspaceId(), dto.getUserId(), scene,
                dto.getInputJson(), dto.getIdempotencyKey());
    }

    @Override
    public TaskResultVO queryTask(Long workspaceId, Long taskId) {
        AiTaskEntity task = aiTaskService.get(workspaceId, taskId);
        if (task == null) {
            return null;
        }
        return TaskResultVO.builder()
                .taskId(task.getId())
                .scene(task.getScene())
                .status(task.getStatus())
                .resultJson(task.getResultJson())
                .errorMsg(task.getErrorMsg())
                .build();
    }

    @Override
    public String findLatestSummary(Long workspaceId, Long knowledgeId) {
        if (workspaceId == null || knowledgeId == null) {
            return null;
        }
        // workspaceId 与发布事件对齐（摘要落库在知识归属空间），created_at 倒序取最新一条
        AiEnhanceResultEntity result = enhanceResultMapper.selectOne(Wrappers.<AiEnhanceResultEntity>lambdaQuery()
                .eq(AiEnhanceResultEntity::getWorkspaceId, workspaceId)
                .eq(AiEnhanceResultEntity::getKnowledgeId, knowledgeId)
                .eq(AiEnhanceResultEntity::getScene, AiScene.SUMMARY.name())
                .orderByDesc(AiEnhanceResultEntity::getCreatedAt)
                .last("LIMIT 1"));
        if (result == null || StrUtil.isBlank(result.getResultJson())) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(result.getResultJson());
            return StrUtil.blankToDefault(obj.getStr("summary"), null);
        } catch (Exception e) {
            // 历史脏数据容错：解析失败按无摘要处理，不阻断详情页
            log.warn("摘要 result_json 解析失败（按无摘要处理）：knowledgeId={}", knowledgeId, e);
            return null;
        }
    }

    @Override
    public void generateSummary(Long workspaceId, Long knowledgeId, String title, String content) {
        // 与发布事件监听同款生成落库路径（EnhanceService.generateAndStoreSummary）；
        // AI 输出异常由其抛 BizException，本层不吞错（触发方 publishing 决定前端提示）
        enhanceService.generateAndStoreSummary(workspaceId, knowledgeId, title, content);
    }
}
