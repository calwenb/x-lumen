package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.publishing.dto.ApproveDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.dto.RejectDTO;
import com.calwen.xlumen.publishing.vo.ReviewVO;

/**
 * 审核服务（F-0902/F-0903）：提交审核/列表/详情/通过/驳回，状态流转规则集中本服务。
 * 工作空间与用户上下文从 WorkspaceContext 读取；文章状态经 ContentApi.publishArticle 迁移（乐观锁）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ReviewService {

    /**
     * 提交审核（F-0902）：文章状态须为 DRAFT/APPROVED；强制审核关闭时直接通过，否则提交 AI 审校任务。
     *
     * @param articleId 文章 ID
     * @return 审核记录视图
     */
    ReviewVO submitReview(Long articleId);

    /**
     * 分页查询当前空间的审核记录。
     *
     * @param status   状态（可空）
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 审核记录分页
     */
    PageResult<ReviewVO> listReviews(String status, long pageNo, long pageSize);

    /**
     * 审核详情（越权返回 404）。
     *
     * @param reviewId 审核记录 ID
     * @return 审核记录视图
     */
    ReviewVO getReview(Long reviewId);

    /**
     * 审核通过（F-0903）：版本校验 + AI 审校完成校验，文章迁移 APPROVED(4)。
     *
     * @param reviewId 审核记录 ID
     * @param dto      版本号
     * @return 审核记录视图
     */
    ReviewVO approve(Long reviewId, ApproveDTO dto);

    /**
     * 审核驳回（F-0903）：驳回三要素必填 + 版本校验，文章回 DRAFT(2)，写审计 REVIEW_REJECT。
     *
     * @param reviewId 审核记录 ID
     * @param dto      驳回入参
     */
    void reject(Long reviewId, RejectDTO dto);
}
