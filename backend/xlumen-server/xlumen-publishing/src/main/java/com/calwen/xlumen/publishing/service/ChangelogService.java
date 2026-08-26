package com.calwen.xlumen.publishing.service;

import com.calwen.xlumen.publishing.dto.ChangelogDTO;
import com.calwen.xlumen.publishing.dto.ChangelogPageVO;

/**
 * 站点更新日志服务：管理面增改删/发布切换 + 公开分页（仅已发布）。
 *
 * @author calwen
 * @date 2026/8/26
 */
public interface ChangelogService {

    ChangelogPageVO adminPage(Long workspaceId, long pageNo, long pageSize);

    Long create(Long workspaceId, ChangelogDTO dto);

    void update(Long workspaceId, Long id, ChangelogDTO dto);

    void delete(Long workspaceId, Long id);

    ChangelogPageVO publicPage(Long workspaceId, long pageNo, long pageSize);
}