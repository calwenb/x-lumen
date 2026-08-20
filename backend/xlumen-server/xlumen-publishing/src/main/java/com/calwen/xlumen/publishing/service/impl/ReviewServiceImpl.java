package com.calwen.xlumen.publishing.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.calwen.xlumen.ai.api.AiApi;
import com.calwen.xlumen.ai.api.dto.SubmitTaskDTO;
import com.calwen.xlumen.ai.api.vo.TaskResultVO;
import com.calwen.xlumen.ai.enums.AiScene;
import com.calwen.xlumen.ai.enums.AiTaskStatus;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.enums.KnowledgeStatus;
import com.calwen.xlumen.identity.api.WorkspaceApi;
import com.calwen.xlumen.identity.service.ActivityLogService;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import com.calwen.xlumen.publishing.dto.ApproveDTO;
import com.calwen.xlumen.publishing.dto.AutoPublishDTO;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.dto.RejectDTO;
import com.calwen.xlumen.publishing.dto.ReviewQueryDTO;
import com.calwen.xlumen.publishing.entity.ReviewEntity;
import com.calwen.xlumen.publishing.mapper.ReviewMapper;
import com.calwen.xlumen.publishing.service.ReviewService;
import com.calwen.xlumen.publishing.service.ReleaseService;
import com.calwen.xlumen.publishing.vo.ReleaseVO;
import com.calwen.xlumen.publishing.vo.ReviewVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核服务实现（F-0902/F-0903）：状态流转规则集中本服务（禁 Controller 判断状态）。
 * 知识状态经 ContentApi.publishKnowledge 乐观锁迁移；AI 审校任务经 AiApi 异步提交。
 *
 * @author calwen
 * @date 2026/8/13
 */
@Service("publishingReviewService")
public class ReviewServiceImpl implements ReviewService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final JsonMapper JSON = new JsonMapper();

    @Resource
    private ReviewMapper reviewMapper;

    @Resource
    private ContentApi contentApi;

    @Resource
    private KnowledgeApi knowledgeApi;

    @Resource
    private AiApi aiApi;

    @Resource
    private WorkspaceApi workspaceApi;

    @Resource
    private ReleaseService releaseService;

    @Resource
    private ActivityLogService activityLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO submitReview(Long knowledgeId) {
        return submitReview(knowledgeId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO submitAutoReview(Long knowledgeId) {
        ReviewVO review = submitReview(knowledgeId, true);
        return getReview(review.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    private ReviewVO submitReview(Long knowledgeId, boolean forceAi) {
        Long workspaceId = WorkspaceContext.workspaceId();
        Long userId = WorkspaceContext.userId();
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        KnowledgeStatus status = KnowledgeStatus.of(knowledge.getStatus());
        if (status != KnowledgeStatus.DRAFT && status != KnowledgeStatus.APPROVED) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态不可提交审核");
        }
        // 归属兜底（决策 D16）：知识必须归属有效的知识库/目录，拦截历史孤儿数据（BUG-4 防线）
        if (knowledge.getKbId() == null
                || !knowledgeApi.checkOwnership(workspaceId, knowledge.getKbId(), knowledge.getDirectoryId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "知识未归属有效知识库，请先在编辑器中选择知识库与目录");
        }
        if (forceAi) {
            ReviewEntity existing = reviewMapper.selectOne(Wrappers.<ReviewEntity>lambdaQuery()
                    .eq(ReviewEntity::getWorkspaceId, workspaceId)
                    .eq(ReviewEntity::getKnowledgeId, knowledgeId)
                    .eq(ReviewEntity::getVersion, knowledge.getVersion())
                    .in(ReviewEntity::getStatus, STATUS_PENDING, STATUS_APPROVED)
                    .orderByDesc(ReviewEntity::getUpdatedAt)
                    .last("LIMIT 1"));
            if (existing != null) return toVO(existing);
        }
        ReviewEntity review = new ReviewEntity();
        review.setWorkspaceId(workspaceId);
        review.setKnowledgeId(knowledgeId);
        review.setKnowledgeTitle(knowledge.getTitle());
        review.setVersion(knowledge.getVersion());
        review.setReviewerId(userId);
        review.setStatus(STATUS_PENDING);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        // 强制审核关闭 → 直接通过；否则提交 AI 审校任务（场景 REVIEWER，幂等键 review-{id}）
        boolean forced = forceAi || !Boolean.FALSE.equals(workspaceApi.forceReviewEnabled(workspaceId));
        if (!forced) {
            review.setStatus(STATUS_APPROVED);
        } else {
            SubmitTaskDTO task = SubmitTaskDTO.builder()
                    .workspaceId(workspaceId).userId(userId).scene(AiScene.REVIEWER.name())
                    .inputJson(buildReviewInput(knowledgeId, knowledge.getTitle(), knowledge.getContent()))
                    .idempotencyKey("review-" + review.getId())
                    .build();
            review.setAiTaskId(aiApi.submitTask(task));
        }
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);

        int target = STATUS_APPROVED.equals(review.getStatus())
                ? KnowledgeStatus.APPROVED.getValue() : KnowledgeStatus.PENDING_REVIEW.getValue();
        boolean ok = contentApi.publishKnowledge(workspaceId, KnowledgePublishDTO.builder()
                .knowledgeId(knowledgeId).expectedVersion(knowledge.getVersion()).targetStatus(target).build());
        if (!ok) {
            throw new BizException(ErrorCode.CONFLICT, "知识状态迁移失败，版本冲突");
        }
        return toVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReleaseVO publishAfterAutoReview(Long reviewId, AutoPublishDTO dto) {
        ReviewEntity review = getOwnedReview(reviewId);
        if (STATUS_APPROVED.equals(review.getStatus())) {
            ReleaseVO existing = releaseService.findByKnowledgeVersion(review.getKnowledgeId(), review.getVersion());
            if (existing != null) return existing;
            throw new BizException(ErrorCode.CONFLICT, "该审核已通过但发布记录不存在，请联系管理员");
        }
        if (!STATUS_PENDING.equals(review.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "该审核已被阻断，请修改后重新发布");
        }
        TaskResultVO task = review.getAiTaskId() == null
                ? null : aiApi.queryTask(WorkspaceContext.workspaceId(), review.getAiTaskId());
        if (task == null || !AiTaskStatus.COMPLETED.name().equals(task.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "AI 审核尚未完成");
        }
        String resultJson = task.getResultJson();
        try {
            if (containsError(resultJson)) {
                finalizeBlocked(review, "AI 审核发现高风险问题", "请查看 AI 审核问题", "修复 error 问题后重新发布");
                throw new BizException(ErrorCode.CONFLICT, "AI 审核未通过，请先修复高风险问题");
            }
        } catch (BizException e) {
            if (!STATUS_REJECTED.equals(review.getStatus())) {
                finalizeBlocked(review, "AI 审核结果无效", "AI 结果", "请重新发起审核");
            }
            throw e;
        }
        review.setAiResultJson(resultJson);
        review.setStatus(STATUS_APPROVED);
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        migrateKnowledge(review.getKnowledgeId(), KnowledgeStatus.APPROVED.getValue());
        return releaseService.release(CreateReleaseDTO.builder()
                .knowledgeId(review.getKnowledgeId())
                .version(review.getVersion())
                .publishAt(dto == null ? null : dto.getPublishAt())
                .build());
    }

    @Override
    public PageResult<ReviewVO> listReviews(ReviewQueryDTO query) {
        Long workspaceId = WorkspaceContext.workspaceId();
        Page<ReviewEntity> page = reviewMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()),
                Wrappers.<ReviewEntity>lambdaQuery()
                        .eq(ReviewEntity::getWorkspaceId, workspaceId)
                        .eq(StrUtil.isNotBlank(query.getStatus()), ReviewEntity::getStatus, query.getStatus())
                        .orderByDesc(ReviewEntity::getCreatedAt));
        List<ReviewVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.<ReviewVO>builder()
                .total(page.getTotal()).pageNo(page.getCurrent()).pageSize(page.getSize()).records(records).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO getReview(Long reviewId) {
        ReviewEntity review = getOwnedReview(reviewId);
        backfillAiResult(review);
        return enrichAutoState(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO approve(Long reviewId, ApproveDTO dto) {
        ReviewEntity review = getOwnedReview(reviewId);
        if (!dto.getVersion().equals(review.getVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "版本冲突");
        }
        if (review.getAiTaskId() != null) {
            TaskResultVO task = aiApi.queryTask(WorkspaceContext.workspaceId(), review.getAiTaskId());
            if (task == null || !AiTaskStatus.COMPLETED.name().equals(task.getStatus())) {
                throw new BizException(ErrorCode.CONFLICT, "AI 审校未完成");
            }
            review.setAiResultJson(task.getResultJson());
            if (containsError(review.getAiResultJson())) {
                throw new BizException(ErrorCode.CONFLICT, "AI 审核发现高风险问题，不能人工通过");
            }
        }
        review.setStatus(STATUS_APPROVED);
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        migrateKnowledge(review.getKnowledgeId(), KnowledgeStatus.APPROVED.getValue());
        return toVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long reviewId, RejectDTO dto) {
        ReviewEntity review = getOwnedReview(reviewId);
        if (!dto.getVersion().equals(review.getVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "版本冲突");
        }
        backfillAiResult(review);
        review.setStatus(STATUS_REJECTED);
        review.setRejectReason(dto.getReason().trim());
        review.setRejectPosition(dto.getPosition().trim());
        review.setRejectExpectation(dto.getExpectation().trim());
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        migrateKnowledge(review.getKnowledgeId(), KnowledgeStatus.DRAFT.getValue());
        activityLogService.record(WorkspaceContext.workspaceId(), WorkspaceContext.userId(),
                WorkspaceContext.username(), "REVIEW_REJECT", "REVIEW", reviewId, null);
    }

    /** 按当前空间与 ID 取审核记录，越权/不存在统一 404。 */
    private ReviewEntity getOwnedReview(Long reviewId) {
        ReviewEntity review = reviewMapper.selectOne(Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getId, reviewId)
                .eq(ReviewEntity::getWorkspaceId, WorkspaceContext.workspaceId()));
        if (review == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审核记录不存在");
        }
        return review;
    }

    /**
     * BUG-003 懒回填：审核记录尚未落 AI 结果且任务已完成时，从 ai_task 拉取快照写回 pub_review.ai_result_json。
     * 幂等（已有结果即跳过）；回填失败不阻断读取/流转，下次访问重试。
     */
    private void backfillAiResult(ReviewEntity review) {
        if (review.getAiTaskId() == null || StrUtil.isNotBlank(review.getAiResultJson())) {
            return;
        }
        try {
            TaskResultVO task = aiApi.queryTask(WorkspaceContext.workspaceId(), review.getAiTaskId());
            if (task != null && AiTaskStatus.COMPLETED.name().equals(task.getStatus())
                    && StrUtil.isNotBlank(task.getResultJson())) {
                review.setAiResultJson(task.getResultJson());
                review.setUpdatedAt(LocalDateTime.now());
                reviewMapper.updateById(review);
            }
        } catch (Exception ignored) {
            // AI 任务查询异常不阻断审核详情/驳回主流程
        }
    }

    /** 计算自动发布状态；阻断/失败时幂等地把知识退回草稿，避免卡在待审核。 */
    private ReviewVO enrichAutoState(ReviewEntity review) {
        if (review.getAiTaskId() == null) {
            return toVO(review, "", STATUS_APPROVED.equals(review.getStatus()) ? "PUBLISHED" : "READY", "");
        }
        TaskResultVO task = aiApi.queryTask(WorkspaceContext.workspaceId(), review.getAiTaskId());
        String taskStatus = task == null ? "" : task.getStatus();
        if (task == null || !AiTaskStatus.COMPLETED.name().equals(taskStatus)) {
            if (task != null && (AiTaskStatus.FAILED.name().equals(taskStatus)
                    || AiTaskStatus.CANCELLED.name().equals(taskStatus))) {
                finalizeBlocked(review, "AI 审核执行失败", "AI 任务", task.getErrorMsg());
                return toVO(review, taskStatus, "FAILED", task.getErrorMsg());
            }
            return toVO(review, taskStatus, "REVIEWING", task == null ? "" : task.getErrorMsg());
        }
        String resultJson = task.getResultJson();
        try {
            if (containsError(resultJson)) {
                finalizeBlocked(review, "AI 审核发现高风险问题", "请查看 AI 审核问题", "修复 error 问题后重新发布");
                return toVO(review, taskStatus, "BLOCKED", "AI 审核发现高风险问题");
            }
        } catch (BizException e) {
            finalizeBlocked(review, "AI 审核结果无效", "AI 结果", "请重新发起审核");
            return toVO(review, taskStatus, "BLOCKED", e.getMessage());
        }
        return toVO(review, taskStatus, STATUS_APPROVED.equals(review.getStatus()) ? "PUBLISHED" : "READY", "");
    }

    private boolean containsError(String resultJson) {
        if (StrUtil.isBlank(resultJson)) return false;
        try {
            JSONArray issues = JSONUtil.parseArray(resultJson);
            return issues.stream().anyMatch(item -> "error".equalsIgnoreCase(JSONUtil.parseObj(item).getStr("severity")));
        } catch (Exception e) {
            throw new BizException(ErrorCode.CONFLICT, "AI 审核结果格式无效，请重试");
        }
    }

    private void finalizeBlocked(ReviewEntity review, String reason, String position, String expectation) {
        if (!STATUS_PENDING.equals(review.getStatus())) return;
        review.setStatus(STATUS_REJECTED);
        review.setRejectReason(reason);
        review.setRejectPosition(position);
        review.setRejectExpectation(expectation);
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        migrateKnowledge(review.getKnowledgeId(), KnowledgeStatus.DRAFT.getValue());
    }

    /** 知识状态迁移：重读当前版本做乐观锁，失败抛 409。 */
    private void migrateKnowledge(Long knowledgeId, int targetStatus) {
        Long workspaceId = WorkspaceContext.workspaceId();
        EditorKnowledgeDTO knowledge = contentApi.getEditorKnowledge(workspaceId, knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识不存在");
        }
        boolean ok = contentApi.publishKnowledge(workspaceId, KnowledgePublishDTO.builder()
                .knowledgeId(knowledgeId).expectedVersion(knowledge.getVersion()).targetStatus(targetStatus).build());
        if (!ok) {
            throw new BizException(ErrorCode.CONFLICT, "知识状态迁移失败，版本冲突");
        }
    }

    /** AI 审校任务入参：{"knowledgeId","title","content"}。 */
    private String buildReviewInput(Long knowledgeId, String title, String content) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("knowledgeId", knowledgeId);
        input.put("title", title);
        input.put("content", content);
        return JSON.writeValueAsString(input);
    }

    private ReviewVO toVO(ReviewEntity r) {
        return toVO(r, "", "", "");
    }

    private ReviewVO toVO(ReviewEntity r, String taskStatus, String decision, String errorMessage) {
        return ReviewVO.builder()
                .id(r.getId()).knowledgeId(r.getKnowledgeId()).knowledgeTitle(r.getKnowledgeTitle())
                .version(r.getVersion()).status(r.getStatus()).aiTaskId(r.getAiTaskId())
                .aiResultJson(r.getAiResultJson()).rejectReason(r.getRejectReason())
                .rejectPosition(r.getRejectPosition()).rejectExpectation(r.getRejectExpectation())
                .aiTaskStatus(taskStatus).autoDecision(decision).aiErrorMessage(errorMessage)
                .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();
    }
}
