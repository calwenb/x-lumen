package com.calwen.xlumen.knowledge.service;

import com.calwen.xlumen.knowledge.dto.CreateDirectoryDTO;
import com.calwen.xlumen.knowledge.dto.UpdateDirectoryDTO;
import com.calwen.xlumen.knowledge.vo.DirectoryVO;

import java.util.List;

/**
 * 目录服务（F-0309，决策 D16）：parent_id 多级目录树（0=库根），按名称排序（数据库排序规则，不设拼音列）。
 * 归属按当前会话空间校验（跨空间统一 404）；同级同名冲突 409。
 *
 * @author calwen
 * @date 2026/8/14
 */
public interface DirectoryService {

    /**
     * 目录树（F-0309）：库不存在或跨空间 404；一级目录平铺返回，子目录挂 children（均按名称排序）。
     * 每个目录 knowledgeCount 由 content 侧统计（cnt_knowledge 属 content 模块，本模块依赖方向受限
     * 无法直查），当前恒为 0，待 KB-3 content 改造实现 ContentApi.countKnowledgeByDirectories 后补全。
     *
     * @param kbId 知识库 ID
     * @return 目录树列表（一级目录，含多级 children）
     */
    List<DirectoryVO> tree(Long kbId);

    /**
     * 创建目录（F-0309）：parentId 必须属于同一知识库（0=库根）；同级同名冲突 409。
     *
     * @param kbId 知识库 ID
     * @param dto  创建入参（父目录 ID/名称）
     * @return 创建后的目录视图
     */
    DirectoryVO create(Long kbId, CreateDirectoryDTO dto);

    /**
     * 更新目录（F-0309）：仅名称可改（父目录变更 V2 提供）；改名同级冲突 409。
     *
     * @param kbId        知识库 ID
     * @param directoryId 目录 ID
     * @param dto         更新入参（名称可空=不修改）
     * @return 更新后的目录视图（与 create 契约一致，前端右键菜单重命名后依赖返回值刷新）
     */
    DirectoryVO update(Long kbId, Long directoryId, UpdateDirectoryDTO dto);

    /**
     * 删除目录（F-0309）：目录及其全部子目录删除；目录下知识上挂父目录（根级目录删除挂库根
     * directory_id=0）——cnt_knowledge 属 content 模块，本模块无法直改，通过发布 KbDirectoryDeletedEvent
     * 由 content 侧监听迁移（KB-3 content 改造），本方法保证 kb_directory 侧状态一致。
     *
     * @param kbId        知识库 ID
     * @param directoryId 目录 ID
     */
    void delete(Long kbId, Long directoryId);

    /**
     * 目录归属校验（F-0309 checkOwnership 支撑）：目录存在且属于指定知识库。
     *
     * @param kbId        知识库 ID
     * @param directoryId 目录 ID
     * @return 是否归属合法
     */
    boolean belongsTo(Long kbId, Long directoryId);
}
