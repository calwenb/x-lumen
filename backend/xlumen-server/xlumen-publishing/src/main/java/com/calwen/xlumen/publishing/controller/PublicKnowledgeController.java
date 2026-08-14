package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;
import com.calwen.xlumen.publishing.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.PublicKnowledgeService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客前台公开读接口（F-0201/F-0202，B01/B02/B03）：匿名可访问（SecurityConfig 白名单 /api/v1/public/**）。
 * 工作空间为默认空间（MVP 单空间，决策 D9）；私有/未发布知识不出现（F-0307）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicKnowledgeController {

    @Resource
    private PublicKnowledgeService publicKnowledgeService;

    /**
     * 分页查询公开知识（F-0201/F-0202）：关键词/库(kbId)/目录(directoryId)/标签组合筛选，服务端分页；
     * 查询参数由 KnowledgeQueryDTO 自动绑定（GET），字段默认值即接口默认值。
     */
    @GetMapping("/knowledge")
    public ApiResponse<PageResult<KnowledgeCardVO>> listKnowledge(KnowledgeQueryDTO query) {
        return ApiResponse.success(publicKnowledgeService.listKnowledge(query));
    }

    /**
     * 知识详情（F-0201，B02）：登录用户附带点赞状态（WorkspaceContext）。
     */
    @GetMapping("/knowledge/{id}")
    public ApiResponse<KnowledgeDetailVO> getKnowledge(@PathVariable Long id) {
        return ApiResponse.success(publicKnowledgeService.getKnowledge(id));
    }

    /**
     * 阅读量记录（F-0203）：访客指纹（IP）24 小时窗口内只计一次，防刷。
     */
    @PostMapping("/knowledge/{id}/view")
    public ApiResponse<Boolean> recordView(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(publicKnowledgeService.recordView(id, visitorKey(request)));
    }

    /**
     * 标签聚合（F-0202，B01 侧栏/B03 筛选）。
     */
    @GetMapping("/tags")
    public ApiResponse<List<CategoryCountDTO>> listTags() {
        return ApiResponse.success(publicKnowledgeService.listTags());
    }

    /** 访客指纹：优先取反向代理传递的客户端 IP。 */
    private String visitorKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
