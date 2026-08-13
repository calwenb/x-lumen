package com.calwen.xlumen.ai.service.impl;

import com.calwen.xlumen.ai.api.AiApi;
import com.calwen.xlumen.ai.api.dto.SubmitTaskDTO;
import com.calwen.xlumen.ai.api.vo.TaskResultVO;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.service.AiTaskService;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * AI 模块对外接口实现（BACKEND.md §5.2）：跨模块任务提交与查询（F-1302）。
 * 调用方（content/publishing）通过 AiApi 提交写作/审校任务后轮询结果。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service
public class AiApiImpl implements AiApi {

    private final AiTaskService aiTaskService;

    public AiApiImpl(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
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
}
