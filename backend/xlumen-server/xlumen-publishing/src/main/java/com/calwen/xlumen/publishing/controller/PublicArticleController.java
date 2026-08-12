package com.calwen.xlumen.publishing.controller;

import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.common.web.ApiResponse;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.ArticleCardVO;
import com.calwen.xlumen.publishing.dto.ArticleDetailVO;
import com.calwen.xlumen.publishing.dto.PageResult;
import com.calwen.xlumen.publishing.service.PublicArticleService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客前台公开读接口（F-0201/F-0202，B01/B02/B03）：匿名可访问（SecurityConfig 白名单 /api/v1/public/**）。
 * 工作空间为默认空间（MVP 单空间，决策 D9）；私有/未发布文章不出现（F-0307）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicArticleController {

    @Resource
    private PublicArticleService publicArticleService;

    /**
     * 分页查询公开文章（F-0201/F-0202）：关键词/分类/标签组合筛选，服务端分页。
     *
     * @param keyword  关键词（可空）
     * @param category 分类（可空）
     * @param tag      标签（可空）
     * @param pageNo   页码（默认 1）
     * @param pageSize 每页条数（默认 10，上限 100）
     */
    @GetMapping("/articles")
    public ApiResponse<PageResult<ArticleCardVO>> listArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(publicArticleService.listArticles(keyword, category, tag, pageNo, pageSize));
    }

    /**
     * 文章详情（F-0201，B02）：登录用户附带点赞状态。
     */
    @GetMapping("/articles/{id}")
    public ApiResponse<ArticleDetailVO> getArticle(@PathVariable("id") Long id) {
        return ApiResponse.success(publicArticleService.getArticle(id, WorkspaceContext.userId()));
    }

    /**
     * 阅读量记录（F-0203）：访客指纹（IP）24 小时窗口内只计一次，防刷。
     */
    @PostMapping("/articles/{id}/view")
    public ApiResponse<Boolean> recordView(@PathVariable("id") Long id, HttpServletRequest request) {
        return ApiResponse.success(publicArticleService.recordView(id, visitorKey(request)));
    }

    /**
     * 分类聚合（F-0202，B01 侧栏/B03 筛选）。
     */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryCountDTO>> listCategories() {
        return ApiResponse.success(publicArticleService.listCategories());
    }

    /**
     * 标签聚合（F-0202，B01 侧栏/B03 筛选）。
     */
    @GetMapping("/tags")
    public ApiResponse<List<CategoryCountDTO>> listTags() {
        return ApiResponse.success(publicArticleService.listTags());
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
