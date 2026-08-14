package com.calwen.xlumen.ai.controller;

import cn.hutool.json.JSONUtil;
import com.calwen.xlumen.ai.entity.AiTaskEntity;
import com.calwen.xlumen.ai.enums.AiTaskStatus;
import com.calwen.xlumen.ai.service.AiTaskService;
import com.calwen.xlumen.ai.service.SseService;
import com.calwen.xlumen.ai.vo.TaskVO;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 任务接口（F-1302）：任务状态查询、SSE 事件订阅、失败重试。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final AiTaskService aiTaskService;
    private final SseService sseService;

    public TaskController(AiTaskService aiTaskService, SseService sseService) {
        this.aiTaskService = aiTaskService;
        this.sseService = sseService;
    }

    /**
     * 任务状态与结果查询（按工作空间隔离）。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskVO> get(@PathVariable Long taskId) {
        AiTaskEntity task = aiTaskService.get(WorkspaceContext.workspaceId(), taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return ApiResponse.success(TaskVO.builder()
                .id(task.getId())
                .scene(task.getScene())
                .status(task.getStatus())
                .resultJson(task.getResultJson())
                .errorMsg(task.getErrorMsg())
                .build());
    }

    /**
     * 订阅任务事件（SSE）：已终止直接发 done/error，运行中订阅进度。
     */
    @GetMapping("/{taskId}/events")
    public SseEmitter events(@PathVariable Long taskId) {
        AiTaskEntity task = aiTaskService.get(WorkspaceContext.workspaceId(), taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        AiTaskStatus status = AiTaskStatus.valueOf(task.getStatus());
        if (status.isTerminal()) {
            return terminalEmitter(taskId, task);
        }
        return sseService.subscribe(taskId);
    }

    /**
     * 失败任务有限重试。
     */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<Boolean> retry(@PathVariable Long taskId) {
        return ApiResponse.success(aiTaskService.retry(WorkspaceContext.workspaceId(), taskId));
    }

    /** 已终止任务：返回一次性 emitter，立即发 done/error 后关闭。 */
    private SseEmitter terminalEmitter(Long taskId, AiTaskEntity task) {
        SseEmitter emitter = new SseEmitter(0L);
        String data;
        String event;
        if (AiTaskStatus.COMPLETED.name().equals(task.getStatus())) {
            event = "done";
            data = JSONUtil.toJsonStr(JSONUtil.createObj()
                    .set("taskId", String.valueOf(taskId))
                    .set("resultJson", task.getResultJson()));
        } else if (AiTaskStatus.FAILED.name().equals(task.getStatus())) {
            event = "error";
            data = JSONUtil.toJsonStr(JSONUtil.createObj()
                    .set("taskId", String.valueOf(taskId))
                    .set("message", task.getErrorMsg()));
        } else {
            event = "done";
            data = JSONUtil.toJsonStr(JSONUtil.createObj().set("taskId", String.valueOf(taskId)));
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception ignore) {
            // 一次性 emitter 发送失败直接忽略
        }
        emitter.complete();
        return emitter;
    }
}
