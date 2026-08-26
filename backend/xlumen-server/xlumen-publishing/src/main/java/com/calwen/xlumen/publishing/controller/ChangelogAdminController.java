package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.ChangelogDTO;
import com.calwen.xlumen.publishing.dto.ChangelogPageVO;
import com.calwen.xlumen.publishing.service.ChangelogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站点更新日志管理接口（OWNER）：分页/新增/更新/删除。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/admin/changelogs")
@PreAuthorize("hasRole('OWNER')")
public class ChangelogAdminController {

    private final ChangelogService changelogService;

    public ChangelogAdminController(ChangelogService changelogService) {
        this.changelogService = changelogService;
    }

    @GetMapping
    public ApiResponse<ChangelogPageVO> page(@RequestParam(defaultValue = "1") long pageNo,
                                             @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(changelogService.adminPage(WorkspaceContext.workspaceId(), pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ChangelogDTO dto) {
        Long id = changelogService.create(WorkspaceContext.workspaceId(), dto);
        return ApiResponse.success(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ChangelogDTO dto) {
        changelogService.update(WorkspaceContext.workspaceId(), id, dto);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        changelogService.delete(WorkspaceContext.workspaceId(), id);
        return ApiResponse.success(null);
    }
}