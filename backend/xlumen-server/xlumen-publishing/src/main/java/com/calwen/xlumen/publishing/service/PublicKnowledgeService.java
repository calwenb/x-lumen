package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;
import com.calwen.xlumen.publishing.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.publishing.dto.PageResult;

import java.util.List;

/**
 * 博客前台公开读服务（F-0201/F-0202）：编排 ContentApi 与互动统计。
 * 工作空间取默认空间（MVP 单空间，决策 D9），私有/未发布知识不出现（F-0307 由 ContentApi 保证）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface PublicKnowledgeService {

    /**
     * 分页查询公开知识（含互动统计）。
     *
     * @param query 查询参数（关键词/分类/标签/分页）
     * @return 知识卡片分页
     */
    PageResult<KnowledgeCardVO> listKnowledge(KnowledgeQueryDTO query);

    /**
     * 知识详情（含互动统计与当前用户点赞状态，用户从 WorkspaceContext 读取）。
     *
     * @param knowledgeId 知识 ID
     * @return 详情；不存在抛 404
     */
    KnowledgeDetailVO getKnowledge(Long knowledgeId);

    /**
     * 阅读量防刷自增（F-0203）：同一访客 24 小时内只计一次（Redis 短期状态，决策 D6）。
     *
     * @param knowledgeId  知识 ID
     * @param visitorKey 访客指纹（IP 等）
     * @return 是否本次计为新增阅读
     */
    boolean recordView(Long knowledgeId, String visitorKey);

    /**
     * 分类聚合（F-0202）。
     *
     * @return 分类列表
     */
    List<CategoryCountDTO> listCategories();

    /**
     * 标签聚合（F-0202）。
     *
     * @return 标签列表
     */
    List<CategoryCountDTO> listTags();
}
