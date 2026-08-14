package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.knowledge.dto.CreateKnowledgeBaseDTO;
import com.calwen.xlumen.knowledge.dto.UpdateKnowledgeBaseDTO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;

import java.util.List;

/**
 * 知识库服务（F-0308，决策 D16）：库 CRUD、可见性切换与回收站归属变更，
 * 归属按 WorkspaceContext 会话空间（跨空间统一 404，不暴露资源存在性）。
 *
 * @author calwen
 * @date 2026/8/14
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库（F-0308）：归属当前会话空间；名称空间内唯一（uk_kb_ws_name）冲突 409；
     * 可见性默认私有（0）；雪花 ID；库内知识数初始 0。
     *
     * @param dto 创建入参（名称/简介/封面/可见性）
     * @return 创建后的知识库视图
     */
    KnowledgeBaseVO create(CreateKnowledgeBaseDTO dto);

    /**
     * 更新知识库（F-0308）：不存在或跨空间 404；名称修改后仍唯一校验（冲突 409）；
     * 简介/封面空值不覆盖。
     *
     * @param kbId 知识库 ID
     * @param dto  更新入参（可空字段=不修改）
     * @return 更新后的知识库视图
     */
    KnowledgeBaseVO update(Long kbId, UpdateKnowledgeBaseDTO dto);

    /**
     * 删除知识库（F-0305）：二次确认参数 confirm 必须等于 "CONFIRM"（否则 409）；
     * 库本身软删进回收站（status=1 + deleted_at=NOW()，不扩 8 状态机），并发布
     * KbRecycleStatusEvent(status=1) 供 content 侧连带软删库内知识（方案 §7.2）。
     *
     * @param kbId    知识库 ID
     * @param confirm 二次确认参数（固定值 CONFIRM）
     */
    void delete(Long kbId, String confirm);

    /**
     * 切换知识库可见性（F-0308）：0 私有/1 公开；变更后写审计
     * （action=KB_VISIBILITY_CHANGE, targetType=KNOWLEDGE_BASE）并发布 KbVisibilityChangedEvent
     * 供 publishing 侧失效公开读缓存（缓存一致性由 publishing agent 处理）。
     *
     * @param kbId       知识库 ID
     * @param visibility 目标可见性（0 私有/1 公开）
     * @return 变更后的知识库视图
     */
    KnowledgeBaseVO changeVisibility(Long kbId, Integer visibility);

    /**
     * 空间内知识库列表（F-0308）：status=0（正常）的库，按名称排序（数据库排序规则）。
     * knowledgeCount 由 content 侧统计（cnt_knowledge 属 content 模块，本模块依赖方向受限无法直查），
     * 当前恒为 0，待 KB-3 content 改造实现 ContentApi.countKnowledgeByKbs 后由上层聚合补全。
     *
     * @param workspaceId 工作空间 ID（null 返回空列表）
     * @return 知识库视图列表
     */
    List<KnowledgeBaseVO> list(Long workspaceId);

    /**
     * 知识库详情（F-0308）：不存在或跨空间返回 null（不抛异常，供 Api 层映射）。
     *
     * @param workspaceId 工作空间 ID（null 跳过空间校验，仅查存在性）
     * @param kbId        知识库 ID
     * @return 知识库视图或 null
     */
    KnowledgeBaseVO get(Long workspaceId, Long kbId);

    /**
     * 知识库详情（F-0308，按当前会话空间校验跨空间 404 语义的便捷入口）。
     *
     * @param kbId 知识库 ID
     * @return 知识库视图或 null
     */
    KnowledgeBaseVO get(Long kbId);
}
