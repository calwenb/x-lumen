package com.calwen.xlumen.content.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.dto.KnowledgeListQueryDTO;
import com.calwen.xlumen.content.dto.CreateKnowledgeDTO;
import com.calwen.xlumen.content.dto.DraftSaveDTO;
import com.calwen.xlumen.content.dto.UpdateKnowledgeDTO;
import com.calwen.xlumen.content.service.KnowledgeService;
import com.calwen.xlumen.content.vo.KnowledgeListItemVO;
import com.calwen.xlumen.content.vo.KnowledgeVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识管理接口（F-0301/F-0302/F-0307，B10 创作中心）：需登录（接口权限双层校验第一层）；
 * 资源归属与状态校验在 Service（第二层）。公开读走 publishing 的 /api/v1/public/**（M03）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@PreAuthorize("hasRole('OWNER')")
public class KnowledgeController {

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * 创建知识（F-0301）：新建即草稿。
     */
    @PostMapping
    public ApiResponse<KnowledgeVO> create(@Valid @RequestBody CreateKnowledgeDTO dto) {
        return ApiResponse.success(knowledgeService.create(dto));
    }

    /**
     * 更新知识（F-0301）：版本乐观锁，冲突 409。
     */
    @PutMapping("/{knowledgeId}")
    public ApiResponse<KnowledgeVO> update(@PathVariable Long knowledgeId,
                                         @Valid @RequestBody UpdateKnowledgeDTO dto) {
        return ApiResponse.success(knowledgeService.update(knowledgeId, dto));
    }

    /**
     * 草稿自动保存（F-0302）：幂等，未变化跳过写库。
     */
    @PostMapping("/autosave")
    public ApiResponse<KnowledgeVO> autosave(@Valid @RequestBody DraftSaveDTO dto) {
        return ApiResponse.success(knowledgeService.autosave(dto));
    }

    /**
     * 知识详情（作者本人，含草稿/私有）。
     */
    @GetMapping("/{knowledgeId}")
    public ApiResponse<KnowledgeVO> get(@PathVariable Long knowledgeId) {
        return ApiResponse.success(knowledgeService.get(knowledgeId));
    }

    /**
     * 作者知识列表（B10）：状态/可见性/关键词筛选，查询参数由 KnowledgeListQueryDTO 自动绑定。
     */
    @GetMapping
    public ApiResponse<ContentPageResult<KnowledgeListItemVO>> list(KnowledgeListQueryDTO query) {
        return ApiResponse.success(knowledgeService.list(query));
    }

    /**
     * 删除知识（F-0301）：仅构思/草稿可删除。
     */
    @DeleteMapping("/{knowledgeId}")
    public ApiResponse<Void> delete(@PathVariable Long knowledgeId) {
        knowledgeService.delete(knowledgeId);
        return ApiResponse.success(null);
    }
}
