package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;
import com.calwen.xlumen.publishing.dto.KnowledgeCardVO;
import com.calwen.xlumen.publishing.dto.KnowledgeDetailVO;
import com.calwen.xlumen.publishing.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.publishing.dto.PageResult;

import java.util.List;

/**
 * 博客前台公开读服务：编排 ContentApi 与互动统计。
 * 工作空间取默认空间（MVP 单空间，决策 D9），可见库集合按身份推导，决策 D13），
 * 私有/未发布知识不出现（ ContentApi 保证）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface PublicKnowledgeService {

    /**
     * 分页查询公开知识（含互动统计）。
     *
     * @param query 查询参数（关键词/库/目录/标签/分页）
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
     * 手动触发 AI 摘要生成并入库（详情摘要卡「生成 AI 摘要」）：仅登录用户；
     * 复用发布事件同款落库路径（ai_enhance_result scene=SUMMARY，按知识归属空间落库），
     * 已有摘要时不重复调模型（幂等），生成后精确失效访客详情缓存。
     *
     * @param knowledgeId 知识 ID
     * @return 生成后的最新知识详情（含 aiSummary）；不存在/不可见抛 404，AI 失败抛 503
     */
    KnowledgeDetailVO generateSummary(Long knowledgeId);

    /**
     * 公开知识库探测：公开库返回库信息，私有库/不存在统一 404
     * 「知识库不存在或无权访问」——与知识详情「不可访问」语义一致，避免前端静默回退。
     *
     * @param kbId 知识库 ID
     * @return 公开知识库信息
     */
    KnowledgeBaseVO getKnowledgeBase(Long kbId);

    /**
     * 阅读量防刷自增：同一访客 24 小时内只计一次（Redis 短期状态，决策 D6）。
     *
     * @param knowledgeId  知识 ID
     * @param visitorKey 访客指纹（IP 等）
     * @return 是否本次计为新增阅读
     */
    boolean recordView(Long knowledgeId, String visitorKey);

    /**
     * 标签聚合。
     *
     * @return 标签列表
     */
    List<CategoryCountDTO> listTags();
}
