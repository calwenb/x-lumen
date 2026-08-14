package com.calwen.xlumen.knowledge.api;

import com.calwen.xlumen.knowledge.api.dto.IndexRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchRequestDTO;
import com.calwen.xlumen.knowledge.api.dto.SearchResultDTO;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;
import com.calwen.xlumen.knowledge.vo.KnowledgeBaseVO;

import java.util.List;

/**
 * 知识模块对外接口（BACKEND.md §5.2）：发布即索引（F-0402）与 RAG 检索（F-0404/F-0407），
 * 供 ai 模块对话检索编排（M08）与 publishing 发布事件触发（M10 事件由本模块监听消费）。
 * KB-3 扩展知识库/目录能力（F-0308/F-0309）与可见库集合推导（F-0407 单一实现，决策 D13/D16）。
 * 实现：service/impl/KnowledgeApiImpl。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface KnowledgeApi {

    /**
     * 索引知识（F-0402）：切片→Embedding→写新索引→检索校验→激活→清理旧版本。
     * 异步流水线失败不影响发布（索引状态可查询重试）。
     *
     * @param request 索引请求（含正文快照）
     */
    void indexKnowledge(IndexRequestDTO request);

    /**
     * 移除知识索引（删除/下架同步出索引，F-0402）。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId 知识 ID
     */
    void removeKnowledge(Long workspaceId, Long knowledgeId);

    /**
     * 向量检索（F-0407 按可见库集合过滤，决策 D13）。
     *
     * @param request 检索请求
     * @return 检索结果（按分数降序）
     */
    List<SearchResultDTO> search(SearchRequestDTO request);

    /**
     * 空间内知识库列表（F-0308，不含回收站状态库）。
     *
     * @param workspaceId 工作空间 ID
     * @return 知识库列表（按名称排序）
     */
    List<KnowledgeBaseVO> listKnowledgeBases(Long workspaceId);

    /**
     * 知识库详情（F-0308）：不存在或跨空间返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     * @return 知识库详情或 null
     */
    KnowledgeBaseVO getKnowledgeBase(Long workspaceId, Long kbId);

    /**
     * 跨空间知识库只读查询（多用户公开读，D9 改写）：仅用于已通过可见库集合推导
     * （resolveVisibleKbIds）的库反查名称/可见性（如首页流卡片库 badge、详情面包屑），
     * 调用方必须保证 kbId 在自身可见集合内；库不存在返回 null。
     *
     * @param kbId 知识库 ID
     * @return 知识库详情或 null
     */
    KnowledgeBaseVO getKnowledgeBaseById(Long kbId);

    /**
     * 目录树（F-0309）：parent_id 多级，按名称排序（数据库排序规则）。
     *
     * @param kbId 知识库 ID
     * @return 目录树列表（一级平铺，含子目录）
     */
    List<DirectoryVO> getDirectoryTree(Long kbId);

    /**
     * 库/目录归属校验（F-0309 单库单目录，决策 D16）：directoryId 为 0（库根）时仅校验库；
     * 目录必须属于指定库；不存在或跨空间返回 false。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     * @param directoryId 目录 ID（0=库根）
     * @return 是否归属合法
     */
    boolean checkOwnership(Long workspaceId, Long kbId, Long directoryId);

    /**
     * 可见库集合推导（F-0407 单一实现，决策 D13）：访客=全平台公开库；库主=公开库 + 自己私有库；
     * V2 增加授权库（F-0106）。公开读、搜索、RAG 检索、知识列表共用，禁止散落重复过滤。
     *
     * @param userId 用户 ID（可空=访客）
     * @return 可见知识库 ID 集合（空=无可见库）
     */
    List<Long> resolveVisibleKbIds(Long userId);

    /**
     * 回收站库列表（F-0305）：当前空间 status=1（回收站）的库，deleted_at 降序。
     * 供 publishing 回收站聚合层编排（knowledge 模块依赖方向受限无法直连 content，见 KB-3）。
     *
     * @param workspaceId 工作空间 ID
     * @param pageNo      页码（从 1 开始）
     * @param pageSize    每页条数（≤100）
     * @return 回收站库条目分页
     */
    com.calwen.xlumen.knowledge.dto.PageResult<com.calwen.xlumen.knowledge.vo.RecycleBinItemVO> listRecycledKbs(
            Long workspaceId, long pageNo, long pageSize);

    /**
     * 恢复回收站库（F-0305）：库恢复（status=0+deleted_at=null），连带恢复库内知识
     * （KbRecycleStatusEvent(status=0) 由 content 侧监听）。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     */
    void restoreRecycledKb(Long workspaceId, Long kbId);

    /**
     * 彻底删除回收站库（F-0305）：物理删除库并发布 KbPurgedEvent（content 侧级联删知识）。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     */
    void purgeRecycledKb(Long workspaceId, Long kbId);
}
