package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.ChangelogPageVO;
import com.calwen.xlumen.publishing.service.ChangelogService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点更新日志公开接口：前台展示已发布条目（跨空间聚合，按发布时间倒序）。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/public/changelogs")
public class ChangelogPublicController {

    @Resource
    private ChangelogService changelogService;

    @GetMapping
    public ApiResponse<ChangelogPageVO> page(@RequestParam(defaultValue = "1") long pageNo,
                                             @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(changelogService.publicPage(null, pageNo, pageSize));
    }
}