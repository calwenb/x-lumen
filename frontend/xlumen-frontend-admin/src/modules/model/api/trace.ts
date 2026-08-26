import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// AI 调用追踪接口封装：页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

export interface AiTraceRecord {
  id: string
  scene: string
  provider: string
  model: string
  promptVersion: string
  tokensIn: number
  tokensOut: number
  estCost: number
  success: boolean
  degraded: boolean
  latencyMs: number
  errorMsg: string
  createdAt: string
}

/** 今日汇总统计。 */
export interface AiTraceSummary {
  todayCount: number
  todayFailed: number
  todayByScene: Array<{ scene: string; count: number }>
}

/** 分页查询参数。 */
export interface AiTraceQuery {
  scene?: string
  success?: boolean
  pageNo: number
  pageSize: number
}

/** 分页查询结果。 */
export interface AiTracePage {
  records: AiTraceRecord[]
  total: number
}

/** 后端原始分页响应（Long 序列化为 String，仅 API 层可见）。 */
interface RawAiTracePage {
  records: AiTraceRecord[]
  total: string | number
}

/** 分页查询 AI 调用追踪。 */
export async function fetchAiTraces(query: AiTraceQuery): Promise<AiTracePage> {
  const { data } = await http.get<ApiResponse<RawAiTracePage>>('/admin/ai-traces', {
    params: query,
  })
  const body = unwrap(data)
  return {
    records: body.records,
    total: Number(body.total),
  }
}

/** 查询今日汇总统计。 */
export async function fetchAiTraceSummary(): Promise<AiTraceSummary> {
  const { data } = await http.get<ApiResponse<AiTraceSummary>>('/admin/ai-traces/summary')
  return unwrap(data)
}
