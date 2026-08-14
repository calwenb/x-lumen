package com.calwen.xlumen.content.controller;

import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.dto.ArticleListQueryDTO;
import com.calwen.xlumen.content.dto.CreateArticleDTO;
import com.calwen.xlumen.content.dto.DraftSaveDTO;
import com.calwen.xlumen.content.dto.UpdateArticleDTO;
import com.calwen.xlumen.content.service.ArticleService;
import com.calwen.xlumen.content.vo.ArticleListItemVO;
import com.calwen.xlumen.content.vo.ArticleVO;
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

/**
 * 文章管理接口（F-0301/F-0302/F-0307，B10 创作中心）：需登录（接口权限双层校验第一层）；
 * 资源归属与状态校验在 Service（第二层）。公开读走 publishing 的 /api/v1/public/**（M03）。
 *
 * @author calwen
 * @date 2026/8/13
 */
@RestController
@RequestMapping("/api/v1/articles")
@PreAuthorize("hasRole('OWNER')")
public class ArticleController {

    @Resource
    private ArticleService articleService;

    /**
     * 创建文章（F-0301）：新建即草稿。
     */
    @PostMapping
    public ApiResponse<ArticleVO> create(@Valid @RequestBody CreateArticleDTO dto) {
        return ApiResponse.success(articleService.create(dto));
    }

    /**
     * 更新文章（F-0301）：版本乐观锁，冲突 409。
     */
    @PutMapping("/{articleId}")
    public ApiResponse<ArticleVO> update(@PathVariable Long articleId,
                                         @Valid @RequestBody UpdateArticleDTO dto) {
        return ApiResponse.success(articleService.update(articleId, dto));
    }

    /**
     * 草稿自动保存（F-0302）：幂等，未变化跳过写库。
     */
    @PostMapping("/autosave")
    public ApiResponse<ArticleVO> autosave(@Valid @RequestBody DraftSaveDTO dto) {
        return ApiResponse.success(articleService.autosave(dto));
    }

    /**
     * 文章详情（作者本人，含草稿/私有）。
     */
    @GetMapping("/{articleId}")
    public ApiResponse<ArticleVO> get(@PathVariable Long articleId) {
        return ApiResponse.success(articleService.get(articleId));
    }

    /**
     * 作者文章列表（B10）：状态/可见性/关键词筛选。
     */
    @GetMapping
    public ApiResponse<ContentPageResult<ArticleListItemVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer visibility,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize) {
        ArticleListQueryDTO query = ArticleListQueryDTO.builder()
                .status(status).visibility(visibility).keyword(keyword)
                .pageNo(pageNo).pageSize(pageSize).build();
        return ApiResponse.success(articleService.list(query));
    }

    /**
     * 删除文章（F-0301）：仅构思/草稿可删除。
     */
    @DeleteMapping("/{articleId}")
    public ApiResponse<Void> delete(@PathVariable Long articleId) {
        articleService.delete(articleId);
        return ApiResponse.success(null);
    }
}
