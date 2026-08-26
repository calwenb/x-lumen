package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.RelatedKnowledgeVO;
import com.calwen.xlumen.publishing.service.RelatedService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 相关知识推荐接口（公开）：同标签优先 + 语义检索兜底，访客可用。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/public/knowledge/{knowledgeId}/related")
public class RelatedController {

    @Resource
    private RelatedService relatedService;

    @GetMapping
    public ApiResponse<List<RelatedKnowledgeVO>> related(@PathVariable Long knowledgeId) {
        return ApiResponse.success(relatedService.related(knowledgeId));
    }
}