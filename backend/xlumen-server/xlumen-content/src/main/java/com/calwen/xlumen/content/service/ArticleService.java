package com.calwen.xlumen.content.service;

import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.dto.ArticleListQueryDTO;
import com.calwen.xlumen.content.dto.CreateArticleDTO;
import com.calwen.xlumen.content.dto.DraftSaveDTO;
import com.calwen.xlumen.content.dto.UpdateArticleDTO;
import com.calwen.xlumen.content.vo.ArticleListItemVO;
import com.calwen.xlumen.content.vo.ArticleVO;

/**
 * 文章服务（F-0301/F-0302/F-0307）：CRUD + 草稿自动保存 + 可见性。
 * 作者与空间上下文从 WorkspaceContext 读取（双层校验第二层：所有操作校验资源归属当前空间与作者）。
 * 已发布版本正文不可修改（PRODUCT §4）；版本号乐观锁，冲突 HTTP 409。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ArticleService {

    /**
     * 创建文章（F-0301）：新建即草稿状态。
     *
     * @param dto 创建入参
     * @return 文章视图
     */
    ArticleVO create(CreateArticleDTO dto);

    /**
     * 更新文章（F-0301）：仅构思/草稿状态可编辑；版本校验失败 409。
     *
     * @param articleId 文章 ID
     * @param dto       更新入参（含版本号）
     * @return 更新后视图
     */
    ArticleVO update(Long articleId, UpdateArticleDTO dto);

    /**
     * 草稿自动保存（F-0302）：articleId 为空则新建草稿；内容未变化跳过写库（幂等）。
     *
     * @param dto 保存入参
     * @return 文章视图（含最新版本号）
     */
    ArticleVO autosave(DraftSaveDTO dto);

    /**
     * 查询文章详情（作者本人，含草稿/私有）。
     *
     * @param articleId 文章 ID
     * @return 文章视图
     */
    ArticleVO get(Long articleId);

    /**
     * 分页查询作者文章列表（B10）。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    ContentPageResult<ArticleListItemVO> list(ArticleListQueryDTO query);

    /**
     * 删除文章（F-0301）：仅构思/草稿可删除，已发布需先下架（M10）。
     *
     * @param articleId 文章 ID
     */
    void delete(Long articleId);
}
