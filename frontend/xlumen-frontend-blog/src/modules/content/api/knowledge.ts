// content 模块 API：知识管理（F-0301/F-0302/F-0307，B10 创作中心）。
// ID 与版本为 string（雪花 ID 超出 JS 安全整数，后端 Long 序列化为 String，BACKEND.md §5.3）；
// 统计数值在 API 层 Number() 还原，页面代码不感知。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 状态枚举 → 展示文案（与后端 KnowledgeStatus 一致，F-0901 八状态机）。 */
export const STATUS_LABELS: Record<number, string> = {
  1: '构思',
  2: '草稿',
  3: '待审核',
  4: '已通过',
  5: '定时发布',
  6: '已发布',
  7: '更新中',
  8: '已下架',
}

/** 可见性 → 展示文案（F-0307）。 */
export const VISIBILITY_LABELS: Record<number, string> = {
  0: '私有',
  1: '公开',
}

/** 知识列表项（B10 列表）。 */
export interface KnowledgeListItem {
  id: string
  title: string
  /** 所属知识库 ID（决策 D16，发布目标取自归属）。 */
  kbId: string | null
  /** 所属目录 ID（0=库根）。 */
  directoryId: string | null
  category: string
  tags: string[]
  visibility: number
  status: number
  version: string
  viewCount: number
  updatedAt: string
}

/** 知识编辑详情（含正文）。 */
export interface KnowledgeDetail {
  id: string
  title: string
  content: string
  /** 所属知识库 ID（决策 D16）。 */
  kbId: string | null
  /** 所属目录 ID（0=库根）。 */
  directoryId: string | null
  category: string
  tags: string[]
  visibility: number
  status: number
  version: string
  viewCount: number
  createdAt: string
  updatedAt: string
}

/** 保存入参（创建/更新共用）。 */
export interface KnowledgeSavePayload {
  title: string
  content: string
  category: string
  tags: string[]
  visibility: number
  /** 所属知识库 ID（必填：后端 CreateKnowledgeDTO kbId 非空，决策 D16）。 */
  kbId?: string | null
  /** 所属目录 ID（可空=库根）。 */
  directoryId?: string | null
}

/** 列表查询参数。 */
export interface KnowledgeListQuery {
  status?: number
  visibility?: number
  keyword?: string
  /** 知识库筛选（可空=全部）。 */
  kbId?: string
  /** 目录筛选（0=库根，可空=全部）。 */
  directoryId?: string
  pageNo: number
  pageSize: number
}

/** 分页结果（服务端返回，统计值已还原）。 */
export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}

/** 后端原始响应（Long 序列化为 String，仅 API 层可见）。 */
interface RawKnowledge {
  id: string
  title: string
  content: string
  kbId: string | null
  directoryId: string | null
  category: string
  tags: string[]
  visibility: number
  status: number
  version: string
  viewCount: string
  createdAt: string
  updatedAt: string
}

/** 还原统计数值。 */
function normalize(raw: RawKnowledge): KnowledgeDetail {
  return {
    ...raw,
    viewCount: Number(raw.viewCount),
    kbId: raw.kbId ?? null,
    directoryId: raw.directoryId ?? null,
  }
}

/** 分页查询作者知识列表（F-0301）。 */
export async function fetchKnowledges(query: KnowledgeListQuery): Promise<PageResult<KnowledgeListItem>> {
  const { data } = await http.get<ApiResponse<{ total: string; pageNo: string; pageSize: string; records: RawKnowledge[] }>>(
    '/knowledge',
    { params: query },
  )
  const body = unwrap(data)
  return {
    total: Number(body.total),
    pageNo: Number(body.pageNo),
    pageSize: Number(body.pageSize),
    records: body.records.map((r) => ({
      id: r.id,
      title: r.title,
      kbId: r.kbId ?? null,
      directoryId: r.directoryId ?? null,
      category: r.category,
      tags: r.tags,
      visibility: r.visibility,
      status: r.status,
      version: r.version,
      viewCount: Number(r.viewCount),
      updatedAt: r.updatedAt,
    })),
  }
}

/** 知识详情（作者本人，含草稿/私有）。 */
export async function fetchKnowledge(id: string): Promise<KnowledgeDetail> {
  const { data } = await http.get<ApiResponse<RawKnowledge>>(`/knowledge/${id}`)
  return normalize(unwrap(data))
}

/** 创建知识（F-0301）：新建即草稿。 */
export async function createKnowledge(payload: KnowledgeSavePayload): Promise<KnowledgeDetail> {
  const { data } = await http.post<ApiResponse<RawKnowledge>>('/knowledge', payload)
  return normalize(unwrap(data))
}

/** 更新知识（F-0301）：携带版本号乐观锁，冲突 409。 */
export async function updateKnowledge(id: string, version: string, payload: KnowledgeSavePayload): Promise<KnowledgeDetail> {
  const { data } = await http.put<ApiResponse<RawKnowledge>>(`/knowledge/${id}`, { ...payload, version })
  return normalize(unwrap(data))
}

/** 草稿自动保存（F-0302）：knowledgeId 为空新建草稿；服务端幂等去重。 */
export async function autosaveDraft(payload: {
  knowledgeId?: string
  title?: string
  content?: string
  category?: string
  tags?: string[]
  visibility?: number
  version?: string
}): Promise<KnowledgeDetail> {
  const { data } = await http.post<ApiResponse<RawKnowledge>>('/knowledge/autosave', payload)
  return normalize(unwrap(data))
}

/** 删除知识（F-0301）：仅构思/草稿可删除。 */
export async function deleteKnowledge(id: string): Promise<void> {
  await http.delete<ApiResponse<null>>(`/knowledge/${id}`)
}
