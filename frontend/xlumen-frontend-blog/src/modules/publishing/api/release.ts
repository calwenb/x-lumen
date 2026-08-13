// publishing 模块 API：发布管理（F-0905/F-0906，B13，对应后端 xlumen-publishing release 域）。
// ID/版本为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）；
// 分页/可见性数值在 API 层 Number() 还原。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'
import type { PageResult } from '@/modules/publishing/api/public'

/** 发布入参（立即发布不带 publishAt，定时发布带 publishAt）。 */
export interface ReleaseRequest {
  articleId: string
  version: string
  visibility: number
  publishAt?: string
}

/** 发布提交结果。 */
export interface ReleaseSubmitted {
  id: string
  status: string
  releasedAt: string | null
}

/** 发布记录。 */
export interface ReleaseVO {
  id: string
  articleId: string
  articleTitle: string
  version: string
  visibility: number
  publishAt: string | null
  releasedAt: string | null
  status: string
  createdAt: string
}

interface RawReleaseSubmitted {
  id: string
  status: string
  releasedAt: string | null
}

interface RawRelease {
  id: string
  articleId: string
  articleTitle: string
  version: string
  visibility: string
  publishAt: string | null
  releasedAt: string | null
  status: string
  createdAt: string
}

interface RawReleasePage {
  total: string
  pageNo: string
  pageSize: string
  records: RawRelease[]
}

function normalizeRelease(raw: RawRelease): ReleaseVO {
  return {
    id: String(raw.id),
    articleId: String(raw.articleId),
    articleTitle: raw.articleTitle ?? '',
    version: String(raw.version ?? ''),
    visibility: Number(raw.visibility ?? 0),
    publishAt: raw.publishAt ?? null,
    releasedAt: raw.releasedAt ?? null,
    status: raw.status ?? '',
    createdAt: raw.createdAt ?? '',
  }
}

/** 提交发布（F-0905，立即/定时）。 */
export async function createRelease(payload: ReleaseRequest): Promise<ReleaseSubmitted> {
  const { data } = await http.post<ApiResponse<RawReleaseSubmitted>>('/releases', payload)
  const result = unwrap(data)
  return {
    id: String(result.id),
    status: result.status,
    releasedAt: result.releasedAt ?? null,
  }
}

/** 分页查询发布记录（F-0906）。 */
export async function fetchReleases(query: { pageNo: number; pageSize: number }): Promise<PageResult<ReleaseVO>> {
  const { data } = await http.get<ApiResponse<RawReleasePage>>('/releases', { params: query })
  const page = unwrap(data)
  return {
    total: Number(page.total),
    pageNo: Number(page.pageNo),
    pageSize: Number(page.pageSize),
    records: page.records.map(normalizeRelease),
  }
}
