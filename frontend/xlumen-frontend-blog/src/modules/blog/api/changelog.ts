// blog 模块 API：前台更新日志（站点动态，公开读，对应后端公开域 changelogs 接口）。
// ID 类字段后端 Long 序列化为 String，此处按数值还原；publishedAt 可能为 null（未发布）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 更新日志条目。 */
export interface ChangelogItem {
  id: number
  title: string
  content: string
  publishedAt: string | null
}

/** 服务端分页结果（结构与 publishing 公开分页一致）。 */
export interface ChangelogPage {
  total: number
  pageNo: number
  pageSize: number
  records: ChangelogItem[]
}

/** 还原后端 Long→String 的数值。 */
function toNumber(value: unknown): number {
  return Number(value ?? 0)
}

/** 分页查询更新日志（默认按页码加载，页内按发布时间倒序）。 */
export async function fetchChangelogs(
  query: { pageNo?: number; pageSize?: number } = {},
): Promise<ChangelogPage> {
  const { data } = await http.get<ApiResponse<RawPage<RawChangelog>>>('/public/changelogs', {
    params: query,
  })
  const body = unwrap(data)
  return {
    total: toNumber(body.total),
    pageNo: toNumber(body.pageNo),
    pageSize: toNumber(body.pageSize),
    records: body.records.map((item) => ({ ...item, id: toNumber(item.id) })),
  }
}

interface RawChangelog {
  id: string
  title: string
  content: string
  publishedAt: string | null
}

interface RawPage<T> {
  total: string
  pageNo: string
  pageSize: string
  records: T[]
}
