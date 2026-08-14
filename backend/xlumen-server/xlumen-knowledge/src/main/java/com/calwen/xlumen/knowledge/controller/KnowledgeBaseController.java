package com.calwen.xlumen.knowledge.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.knowledge.dto.CreateKnowledgeBaseDTO;
import com.calwen.xlumen.knowledge.dto.UpdateKnowledgeBaseDTO;
import com.calwen.xlumen.knowledge.service.KnowledgeBaseService;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（F-0308，B22 知识库管理）：需登录（接口权限双层校验第一层），
 * 资源归属与二次确认校验在 Service（第二层）；可见性切换独立接口（审计+缓存失效事件）。
 *
 * @author calwen
 * @date 2026/8/14
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
@PreAuthorize("hasRole('OWNER')")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 我的知识库列表（F-0308）：当前空间 status=0（正常）的库，按名称排序。
     *
     * @return 知识库列表
     */
    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        return ApiResponse.success(knowledgeBaseService.list(requireWorkspace()));
    }

    /**
     * 创建知识库（F-0308）：名称空间内唯一，冲突 409。
     *
     * @param dto 创建入参
     * @return 创建后的知识库
     */
    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody CreateKnowledgeBaseDTO dto) {
        return ApiResponse.success(knowledgeBaseService.create(dto));
    }

    /**
     * 知识库详情（F-0308）：不存在或跨空间 404。
     *
     * @param kbId 知识库 ID
     * @return 知识库详情
     */
    @GetMapping("/{kbId}")
    public ApiResponse<KnowledgeBaseVO> get(@PathVariable Long kbId) {
        KnowledgeBaseVO vo = knowledgeBaseService.get(requireWorkspace(), kbId);
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return ApiResponse.success(vo);
    }

    /**
     * 更新知识库（F-0308）：名称/简介/封面可改（空值不覆盖）。
     *
     * @param kbId 知识库 ID
     * @param dto  更新入参
     * @return 更新后的知识库
     */
    @PutMapping("/{kbId}")
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable Long kbId,
                                               @Valid @RequestBody UpdateKnowledgeBaseDTO dto) {
        return ApiResponse.success(knowledgeBaseService.update(kbId, dto));
    }

    /**
     * 删除知识库（F-0305）：二次确认参数 confirm=CONFIRM；库与库内知识连带进回收站（方案 §7.2）。
     *
     * @param kbId    知识库 ID
     * @param confirm 二次确认参数（固定值 CONFIRM）
     * @return 空响应
     */
    @DeleteMapping("/{kbId}")
    public ApiResponse<Void> delete(@PathVariable Long kbId, @RequestParam(required = false) String confirm) {
        knowledgeBaseService.delete(kbId, confirm);
        return ApiResponse.success(null);
    }

    /**
     * 切换知识库可见性（F-0308）：0 私有/1 公开，变更即时生效（公开读缓存由 publishing 侧按事件失效）。
     *
     * @param kbId      知识库 ID
     * @param visibility 变更入参 {visibility: 0|1}
     * @return 变更后的知识库
     */
    @PutMapping("/{kbId}/visibility")
    public ApiResponse<KnowledgeBaseVO> changeVisibility(@PathVariable Long kbId,
                                                         @RequestBody Map<String, Integer> visibility) {
        Integer value = visibility == null ? null : visibility.get("visibility");
        return ApiResponse.success(knowledgeBaseService.changeVisibility(kbId, value));
    }

    /** 从会话上下文取工作空间 ID，未登录抛 401。 */
    private Long requireWorkspace() {
        Long workspaceId = WorkspaceContext.workspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return workspaceId;
    }
}
