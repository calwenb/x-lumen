package com.calwen.xlumen.publishing.job;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.ai.enums.AiTaskStatus;
import com.calwen.xlumen.common.event.AiTaskCompletedEvent;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * AI 审校任务状态镜像监听：AI 任务完结事件驱动写入 pub_review.ai_status/ai_error，
 * 并把 COMPLETED 的结果 JSON 懒填 ai_result_json 快照——审核详情/列表展示直接读表，
 * 不再依赖每次请求实时回查 AI 模块（解耦点：本监听不调用 AiApi）。
 * 仅处理 REVIEWER 场景；落库/查询异常一律捕获不抛出，不阻断发布方与同事件的其他监听。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Component
public class AiReviewTaskStatusListener {

    private static final Logger log = LoggerFactory.getLogger(AiReviewTaskStatusListener.class);

    private static final String SCENE_REVIEWER = "REVIEWER";

    private final ReviewMapper reviewMapper;

    public AiReviewTaskStatusListener(ReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    @EventListener
    public void onTaskCompleted(AiTaskCompletedEvent event) {
        if (event == null || event.getTaskId() == null || event.getWorkspaceId() == null
                || !SCENE_REVIEWER.equals(event.getScene())) {
            return;
        }
        ReviewEntity review = reviewMapper.selectOne(Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getWorkspaceId, event.getWorkspaceId())
                .eq(ReviewEntity::getAiTaskId, event.getTaskId())
                .last("LIMIT 1"));
        if (review == null) {
            return;
        }
        try {
            review.setAiStatus(event.getStatus());
            if (AiTaskStatus.COMPLETED.name().equals(event.getStatus())
                    && StrUtil.isNotBlank(event.getResultJson())
                    && StrUtil.isBlank(review.getAiResultJson())) {
                review.setAiResultJson(event.getResultJson());
            } else if (AiTaskStatus.FAILED.name().equals(event.getStatus())) {
                review.setAiError(event.getErrorMsg());
            }
            review.setUpdatedAt(LocalDateTime.now());
            reviewMapper.updateById(review);
        } catch (Exception e) {
            log.warn("AI 审校任务状态镜像落库失败 taskId={} status={}", event.getTaskId(), event.getStatus(), e);
            // 不抛出：状态镜像为展示优化，失败由读取路径（backfillAiResult）实时查询兜底
        }
    }
}