package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.dto.PageQueryDTO;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.FavoriteService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识收藏接口（F-0212，B02 收藏按钮）：收藏 toggle 与我的收藏列表均需登录。
 * 用户/空间上下文由 Service 从 WorkspaceContext 读取（JWT claims），资源归属校验在服务层（双层校验第二层）。
 *
 * @author calwen
 * @date 2026/8/18
 */
@RestController
@RequestMapping("/api/v1/public")
public class FavoriteController {

    @Resource
    private FavoriteService favoriteService;

    /**
     * 收藏/取消收藏（F-0212）：切换语义，需登录。
     *
     * @return 切换后的状态（true 已收藏 / false 取消）
     */
    @PostMapping("/knowledge/{knowledgeId}/favorite")
    public ApiResponse<Boolean> toggleFavorite(@PathVariable Long knowledgeId) {
        return ApiResponse.success(favoriteService.toggleFavorite(knowledgeId));
    }

    /**
     * 我的收藏列表（F-0212）：需登录，按收藏时间倒序分页（默认 1/10），
     * 卡片复用公开列表 VO 并额外携带 favoritedAt。
     */
    @GetMapping("/favorites")
    public ApiResponse<PageResult<KnowledgeCardVO>> listFavorites(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize) {
        PageQueryDTO query = new PageQueryDTO();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(favoriteService.listFavorites(query));
    }
}
