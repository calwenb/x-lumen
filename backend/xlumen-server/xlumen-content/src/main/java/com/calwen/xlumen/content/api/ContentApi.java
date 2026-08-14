package com.calwen.xlumen.content.api;

import com.calwen.xlumen.content.api.dto.KnowledgeDetailDTO;
import com.calwen.xlumen.content.api.dto.KnowledgePublishDTO;
import com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO;
import com.calwen.xlumen.content.api.dto.CategoryCountDTO;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.EditorKnowledgeDTO;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 内容模块对外接口（BACKEND.md §5.2）：公开读能力供 publishing 模块编排（博客前台公开读）。
 * KB-3（决策 D16）起可见性由知识库决定（文章级 visibility 已删除）：只暴露已发布、不在回收站、
 * 且所属知识库在可见库集合内的知识（F-0407 公开读按身份聚合，可见库集合由 publishing 推导传入）。
 *
 * @author calwen
 * @date 2026/8/12
 */
public interface ContentApi {

    /**
     * 分页查询公开知识（F-0201/F-0202/F-0407）：已发布 + 不在回收站 + 所属库在可见集合内，
     * 支持关键词（标题/摘要 LIKE）、标签、库/目录筛选。可见库集合为空时返回空页（无可见库）。
     * 排序：未选目录按 updated_at 倒序；选中目录（库内浏览）按 created_at 正序（决策 D16）。
     *
     * @param workspaceId 工作空间 ID
     * @param query       查询参数（关键词/标签/库/目录/可见库集合/分页）
     * @return 公开知识分页
     */
    ContentPageResult<PublishedKnowledgeDTO> listPublished(Long workspaceId, KnowledgeQueryDTO query);

    /**
     * 查询公开知识详情（F-0201/F-0407）：仅已发布、不在回收站、且所属库在可见集合内的知识；
     * 不存在或不可见返回 null。
     *
     * @param workspaceId   工作空间 ID
     * @param knowledgeId   知识 ID
     * @param visibleKbIds  可见库集合（由 publishing 按身份推导传入；为空=无可见库返回 null）
     * @return 详情或 null
     */
    KnowledgeDetailDTO getPublished(Long workspaceId, Long knowledgeId, List<Long> visibleKbIds);

    /**
     * 标签聚合（F-0202）：JSON_TABLE 展开标签统计，按数量降序；仅统计已发布且不在回收站的知识。
     * KB-3 起跨空间全平台统计（多用户公开读，D9 改写）：workspaceId 可空=全平台聚合。
     *
     * @param workspaceId 工作空间 ID（可空=跨空间全平台）
     * @return 标签列表
     */
    List<CategoryCountDTO> listTags(Long workspaceId);

    /**
     * 阅读量自增（F-0203）：publishing 侧 Redis 防刷通过后调用。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @return 是否自增成功（知识不存在或跨空间返回 false）
     */
    boolean incrementViewCount(Long workspaceId, Long knowledgeId);

    /**
     * 查询编辑态知识（含草稿/私有，M04）：供 publishing 审核读取正文（M10）使用；不存在或跨空间返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId   知识 ID
     * @return 编辑态知识或 null
     */
    EditorKnowledgeDTO getEditorKnowledge(Long workspaceId, Long knowledgeId);

    /**
     * 发布/状态迁移（M10，F-0901/F-0905）：publishing 通过本接口迁移知识状态与发布信息，
     * 版本乐观锁校验，不一致返回 false（由调用方抛 409）。
     * KB-3 起发布目标为 kbId+directoryId（不再传 visibility，可见性由知识库决定，决策 D16）。
     *
     * @param workspaceId 工作空间 ID
     * @param dto         发布入参（知识 ID/期望版本/目标状态/库/目录/发布时间）
     * @return 是否迁移成功
     */
    boolean publishKnowledge(Long workspaceId, KnowledgePublishDTO dto);

    // ==================== KB-3 知识平台化（F-0305/F-0308/F-0309）新增契约 ====================
    // 以下方法由 KB-3 content 改造在 ContentApiImpl 中实现（knowledge 模块仅声明契约，不提供实现）。
    // 背景：cnt_knowledge 属 content 模块；knowledge 模块因依赖环（content→ai→knowledge）无法直连
    // content，删除/恢复/目录迁移等写入联动由 knowledge 侧进程内事件触发（见 knowledge/event/ 包），
    // 本接口方法供 publishing/编排侧复用同一逻辑；回收站知识列表/恢复/彻底删除由本接口承载数据能力。

    /**
     * 回收站知识条目（F-0305，KB-3 content 改造实现的返回类型，嵌套于 ContentApi 保持单文件契约）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class RecycledKnowledgeItem {

        /** 知识 ID。 */
        private Long id;

        /** 标题（回收站名称列）。 */
        private String title;

        /** 所属知识库 ID。 */
        private Long kbId;

        /** 所属知识库名称（冗余展示，可空）。 */
        private String kbName;

        /** 原目录 ID（0=库根）。 */
        private Long directoryId;

        /** 原目录名称（恢复提示用，可空）。 */
        private String directoryName;

        /** 进回收站时间。 */
        private LocalDateTime deletedAt;
    }

    /**
     * 软删库内全部知识（F-0305，KB-3 content 改造实现）：知识库删除进回收站时连带软删
     * （recycle_status=1 + deleted_at=NOW()），幂等（库内无知识也成功，库不存在或跨空间直接返回）。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     */
    void softDeleteKnowledgeByKb(Long workspaceId, Long kbId);

    /**
     * 恢复库内全部知识（F-0305，KB-3 content 改造实现）：知识库从回收站整体恢复时连带恢复
     * （recycle_status=0 + deleted_at=null）；原目录已不存在的知识挂回库根（directory_id=0）。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     */
    void restoreKnowledgeByKb(Long workspaceId, Long kbId);

    /**
     * 批量统计各库未删除知识数（F-0308，KB-3 content 改造实现）：统计口径 recycle_status=0（含草稿），
     * 供知识库列表/详情补全 knowledgeCount（防 N+1）；kbIds 为空返回空 Map。
     *
     * @param workspaceId 工作空间 ID
     * @param kbIds       知识库 ID 集合
     * @return kbId → 未删除知识数（仅返回命中且跨空间过滤后的库）
     */
    Map<Long, Long> countKnowledgeByKbs(Long workspaceId, List<Long> kbIds);

    /**
     * 批量统计各目录未删除知识数（F-0309，KB-3 content 改造实现）：统计口径 recycle_status=0，
     * 供目录树补全 knowledgeCount；directoryIds 为空返回空 Map。
     *
     * @param workspaceId  工作空间 ID
     * @param kbId         知识库 ID
     * @param directoryIds 目录 ID 集合（0=库根目录）
     * @return directoryId → 未删除知识数
     */
    Map<Long, Long> countKnowledgeByDirectories(Long workspaceId, Long kbId, List<Long> directoryIds);

    /**
     * 目录子树知识上挂（F-0309，KB-3 content 改造实现）：目录删除时把 directoryIds（被删目录及
     * 全部子目录）下未删除知识统一迁移到 newDirectoryId（0=库根），即「删除目录时知识上挂父目录」；
     * 幂等（目录集合为空或库内无知识直接返回）。
     *
     * @param workspaceId    工作空间 ID
     * @param kbId           知识库 ID
     * @param directoryIds   被删目录及全部子目录 ID（含自身）
     * @param newDirectoryId 知识迁移目标目录 ID（0=库根）
     */
    void relocateKnowledgeByDirectories(Long workspaceId, Long kbId, List<Long> directoryIds, Long newDirectoryId);

    /**
     * 回收站知识分页（F-0305，KB-3 content 改造实现）：recycle_status=1，deleted_at 降序，
     * 供回收站「知识」Tab 与「全部」聚合；pageSize 上限 100 由调用方截断。
     *
     * @param workspaceId 工作空间 ID
     * @param pageNo      页码（从 1 开始）
     * @param pageSize    每页条数（≤100）
     * @return 回收站知识分页
     */
    ContentPageResult<RecycledKnowledgeItem> listRecycledKnowledge(Long workspaceId, long pageNo, long pageSize);

    /**
     * 单条回收站知识详情（F-0305 恢复冲突判定，KB-3 content 改造实现）：返回知识及其原库/原目录
     * 归属，供恢复方判定「原目录已删除→挂库根」「原库已彻底删除→拒绝恢复」；不存在或跨空间返回 null。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId 知识 ID
     * @return 回收站知识条目或 null
     */
    RecycledKnowledgeItem getRecycledKnowledge(Long workspaceId, Long knowledgeId);

    /**
     * 恢复单条知识（F-0305，KB-3 content 改造实现）：recycle_status=0 + deleted_at=null，
     * directoryId 由调用方完成恢复冲突判定后传入（原目录已删传 0=库根）；不存在、跨空间或
     * 非回收站状态返回 false（由调用方按 404/冲突处理）。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId 知识 ID
     * @param directoryId 恢复目标目录 ID（0=库根）
     * @return 是否恢复成功
     */
    boolean restoreKnowledge(Long workspaceId, Long knowledgeId, Long directoryId);

    /**
     * 彻底删除单条知识（F-0305 回收站清空，KB-3 content 改造实现）：物理删除 cnt_knowledge 一行；
     * 索引清理由调用方在删除前后经 KnowledgeApi.removeKnowledge 完成；不存在或跨空间返回 false。
     *
     * @param workspaceId 工作空间 ID
     * @param knowledgeId 知识 ID
     * @return 是否删除成功
     */
    boolean purgeKnowledge(Long workspaceId, Long knowledgeId);

    /**
     * 彻底删除库内全部知识（F-0305 库物理级联，KB-3 content 改造实现）：物理删除 cnt_knowledge
     * 中该库全部行（含回收站内）；供 knowledge 侧 KbPurgedEvent 消费方调用；不存在或跨空间直接返回。
     *
     * @param workspaceId 工作空间 ID
     * @param kbId        知识库 ID
     */
    void purgeKnowledgeByKb(Long workspaceId, Long kbId);
}
