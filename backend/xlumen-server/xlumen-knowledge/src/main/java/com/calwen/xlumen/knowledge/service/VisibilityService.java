package com.calwen.xlumen.knowledge.service;

import java.util.List;

/**
 * 可见库集合推导服务（F-0407 单一实现，决策 D13/D16）：访客=全平台公开库；登录用户=公开库 ∪ 自己
 * 空间私有库（V2 增加授权库 F-0106）。公开读、搜索、RAG 检索、知识列表共用，禁止散落重复过滤。
 *
 * @author calwen
 * @date 2026/8/14
 */
public interface VisibilityService {

    /**
     * 推导可见知识库 ID 集合（F-0407）：status=0（正常）的库；userId 为空=访客，仅公开库
     * （visibility=1）；userId 非空=公开库 ∪ 自己空间（MVP 单空间，决策 D9：经 WorkspaceApi 取默认空间）
     * 的全部库。返回按库 ID 升序去重。
     *
     * @param userId 用户 ID（可空=访客）
     * @return 可见知识库 ID 集合（空=无可见库）
     */
    List<Long> resolveVisibleKbIds(Long userId);
}
