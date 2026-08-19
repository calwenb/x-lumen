package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.CreateReleaseDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.ReleaseService;
import com.calwen.xlumen.publishing.vo.ReleaseVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布接口（F-0904/F-0905，B05 内容管理后台）：需登录访问；工作空间上下文取自可信会话（WorkspaceContext）。
 * 发布状态流转与幂等集中在 ReleaseService（禁 Controller 判断状态）。
 * BUG-007/016：补 @PreAuthorize 职责分离（F-0903 发布/下架属内容管理域，需 OWNER 角色），
 * 新增下架端点（F-0906）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/releases")
@PreAuthorize("hasRole('OWNER')")
public class ReleaseController {

    @Resource
    private ReleaseService releaseService;

    /** 创建发布（F-0904）：publishAt 空立即发布，非空留待定时任务。 */
    @PostMapping
    public ApiResponse<ReleaseVO> release(@Valid @RequestBody CreateReleaseDTO dto) {
        return ApiResponse.success(releaseService.release(dto));
    }

    /** 发布记录列表（F-0904）：查询参数由 PageQueryDTO 自动绑定。 */
    @GetMapping
    public ApiResponse<PageResult<ReleaseVO>> listReleases(PageQueryDTO query) {
        return ApiResponse.success(releaseService.listReleases(query));
    }

    /** 下架知识（F-0906，BUG-016）：仅已发布可下架，迁移 UNPUBLISHED(8) 并出索引。 */
    @PostMapping("/{knowledgeId}/unpublish")
    public ApiResponse<Void> unpublish(@PathVariable Long knowledgeId) {
        releaseService.unpublish(knowledgeId);
        return ApiResponse.success(null);
    }
}
