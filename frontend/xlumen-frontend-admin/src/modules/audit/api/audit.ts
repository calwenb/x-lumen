import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// 审计日志接口封装（F-1202）：页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

export interface AuditLogRecord {
  id: string
  operatorName: string
  action: string
  targetType: string
  targetId: string
  detailJson: string
  createdAt: string
}

/** 分页结果（统计数值已还原）。 */
export interface AuditLogPage {
  total: number
  pageNo: number
  pageSize: number
  records: AuditLogRecord[]
}

/** 查询参数。 */
export interface AuditLogQuery {
  pageNo: number
  pageSize: number
  action?: string
}

/** 后端原始响应（Long 序列化为 String，仅 API 层可见）。 */
interface RawAuditPage {
  total: string | number
  pageNo: string | number
  pageSize: string | number
  records: AuditLogRecord[]
}

/** 分页查询审计日志。 */
export async function fetchAuditLogs(query: AuditLogQuery): Promise<AuditLogPage> {
  const { data } = await http.get<ApiResponse<RawAuditPage>>('/admin/audit-logs', {
    params: query,
  })
  const body = unwrap(data)
  return {
    total: Number(body.total),
    pageNo: Number(body.pageNo),
    pageSize: Number(body.pageSize),
    records: body.records,
  }
}
