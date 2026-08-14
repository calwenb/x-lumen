package com.calwen.xlumen.identity.service;

import com.calwen.xlumen.identity.dto.AuditLogQueryDTO;
import com.calwen.xlumen.identity.vo.AuditLogVO;
import com.calwen.xlumen.identity.vo.PageVO;

/**
 * 审计日志服务（F-1202）：只增不改（append-only），供各模块记录关键操作（审核/发布/设置变更）。
 *
 * @author calwen
 * @date 2026/8/13
 */
public interface ActivityLogService {

    /**
     * 记录一条审计日志（F-1202）：operatorId/operatorName 可空（系统或匿名触发）。
     *
     * @param workspaceId  工作空间 ID
     * @param operatorId   操作人用户 ID（可空）
     * @param operatorName 操作人名称（可空）
     * @param action       操作类型（如 REVIEW_REJECT/ARTICLE_PUBLISH）
     * @param targetType   目标类型（如 REVIEW/ARTICLE/WORKSPACE）
     * @param targetId     目标 ID（可空）
     * @param detailJson   操作详情 JSON（可空）
     */
    void record(Long workspaceId, Long operatorId, String operatorName, String action,
                String targetType, Long targetId, String detailJson);

    /**
     * 分页查询当前空间的审计日志（只读）。
     *
     * @param query action 筛选 + 分页参数（action 可空 = 全部）
     * @return 审计日志分页
     */
    PageVO<AuditLogVO> listAuditLogs(AuditLogQueryDTO query);
}
