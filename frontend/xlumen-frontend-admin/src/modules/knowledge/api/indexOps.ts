import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// 全平台索引补跑接口封装（publishing IndexBackfillController）：
// 页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

/** 单条失败明细。 */
export interface ReindexFailedItem {
  knowledgeId: string
  reason: string
}

/** 补跑任务进度快照（服务端内存态，重启即失）。 */
export interface ReindexPlatformProgress {
  /** 本次触发是否真正启动了任务（false=已有任务在跑）。 */
  started: boolean
  /** 任务是否仍在运行。 */
  running: boolean
  /** 触发时刻的全平台已发布知识总数。 */
  total: number
  /** 已处理条数（含失败）。 */
  processed: number
  /** 成功条数。 */
  ok: number
  /** 失败条数。 */
  failedCount: number
  /** 失败明细（最多前 100 条）。 */
  failed: ReindexFailedItem[]
  startedAt: string | null
  finishedAt: string | null
}

/** 后端原始响应（Long 数值字段可能字符串化，API 层统一转 number）。 */
interface RawReindexPlatformProgress extends Omit<
  ReindexPlatformProgress,
  'total' | 'processed' | 'ok' | 'failedCount' | 'failed'
> {
  total: string | number
  processed: string | number
  ok: string | number
  failedCount: string | number
  failed: Array<{ knowledgeId: string | number; reason: string }>
}

function toProgress(raw: RawReindexPlatformProgress): ReindexPlatformProgress {
  return {
    started: raw.started,
    running: raw.running,
    total: Number(raw.total),
    processed: Number(raw.processed),
    ok: Number(raw.ok),
    failedCount: Number(raw.failedCount),
    failed: (raw.failed ?? []).map((item) => ({
      knowledgeId: String(item.knowledgeId),
      reason: item.reason,
    })),
    startedAt: raw.startedAt ?? null,
    finishedAt: raw.finishedAt ?? null,
  }
}

/** 触发全平台补跑（异步、单任务：已有任务在跑时 started=false 并返回当前进度）。 */
export async function triggerReindexAllPlatform(): Promise<ReindexPlatformProgress> {
  const { data } = await http.post<ApiResponse<RawReindexPlatformProgress>>(
    '/knowledge/reindex-all-platform',
  )
  return toProgress(unwrap(data))
}

/** 查询补跑进度（从未触发过时 running=false、total=0）。 */
export async function fetchReindexPlatformStatus(): Promise<ReindexPlatformProgress> {
  const { data } = await http.get<ApiResponse<RawReindexPlatformProgress>>(
    '/knowledge/reindex-all-platform/status',
  )
  return toProgress(unwrap(data))
}
