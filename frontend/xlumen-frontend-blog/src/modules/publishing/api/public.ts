// publishing 模块 API：博客前台公开读（F-0201/F-0202）——知识/标签（后端 xlumen-publishing review/release 域）。
// KB-3 起分类字段废弃（决策 D16 目录树接管），知识卡片改携 kbId/kbName/directoryId，查询改 kbId/directoryId 库级筛选。
// 公开读为匿名接口；评论/点赞（F-0203）由 engagement 模块 API 提供。
// ID 类字段为 string（雪花 ID 超出 JS 安全整数，后端 Long 序列化为 String，BACKEND.md §5.3）；
// 统计/分页数值在 API 层 Number() 还原，页面代码不感知。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 知识卡片（B01 列表，KB-3 起携带库信息，决策 D16）。 */
export interface KnowledgeCard {
  id: string
  title: string
  summary: string
  authorName: string
  kbId: string
  kbName: string
  directoryId: string
  tags: string[]
  viewCount: number
  readMinutes: number
  commentCount: number
  likeCount: number
  publishedAt: string
}

/** 知识详情（B02；F-0212 起携带点踩/收藏统计与收藏态，F-0808 起携带 AI 摘要）。 */
export interface KnowledgeDetail extends KnowledgeCard {
  content: string
  liked: boolean
  updatedAt: string
  dislikeCount: number
  favoriteCount: number
  favorited: boolean
  aiSummary?: string | null
}

/** 标签聚合项。 */
export interface CategoryCount {
  name: string
  count: number
}

/** 服务端分页结果。 */
export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}

export interface KnowledgeQuery {
  keyword?: string
  kbId?: string
  directoryId?: string
  tag?: string
  pageNo?: number
  pageSize?: number
}

/** 还原后端 Long→String 的统计数值。 */
function toNumber(value: unknown): number {
  return Number(value ?? 0)
}

/** 分页查询公开知识（关键词/标签/知识库/目录组合筛选，F-0201/F-0202）。 */
export async function fetchKnowledges(query: KnowledgeQuery): Promise<PageResult<KnowledgeCard>> {
  const { data } = await http.get<ApiResponse<RawPage<RawCard>>>('/public/knowledge', {
    params: query,
  })
  const body = unwrap(data)
  return {
    total: toNumber(body.total),
    pageNo: toNumber(body.pageNo),
    pageSize: toNumber(body.pageSize),
    records: body.records.map((card) => ({
      ...card,
      viewCount: toNumber(card.viewCount),
      commentCount: toNumber(card.commentCount),
      likeCount: toNumber(card.likeCount),
      readMinutes: toNumber(card.readMinutes),
    })),
  }
}

/** 知识详情（F-0201，B02）。 */
export async function fetchKnowledge(id: string): Promise<KnowledgeDetail> {
  const { data } = await http.get<ApiResponse<RawKnowledgeDetail>>(`/public/knowledge/${id}`)
  const knowledge = unwrap(data)
  return {
    ...knowledge,
    viewCount: toNumber(knowledge.viewCount),
    commentCount: toNumber(knowledge.commentCount),
    likeCount: toNumber(knowledge.likeCount),
    readMinutes: toNumber(knowledge.readMinutes),
    dislikeCount: toNumber(knowledge.dislikeCount),
    favoriteCount: toNumber(knowledge.favoriteCount),
  }
}

/** 阅读量上报（F-0203，匿名；同访客 24 小时窗口只计一次）。 */
export async function reportView(id: string): Promise<void> {
  const { data } = await http.post<ApiResponse<boolean>>(`/public/knowledge/${id}/view`)
  unwrap(data)
}

/** 标签聚合（F-0202，B01 侧栏/B03 筛选）。 */
export async function fetchTags(): Promise<CategoryCount[]> {
  const { data } = await http.get<ApiResponse<RawCategoryCount[]>>('/public/tags')
  return unwrap(data).map((item) => ({ ...item, count: toNumber(item.count) }))
}

// 后端原始响应形态（Long 均为 string，由 API 层转换为页面友好类型）
interface RawCard {
  id: string
  title: string
  summary: string
  authorName: string
  kbId: string
  kbName: string
  directoryId: string
  tags: string[]
  viewCount: string
  readMinutes: string
  commentCount: string
  likeCount: string
  publishedAt: string
}

interface RawKnowledgeDetail extends RawCard {
  content: string
  liked: boolean
  updatedAt: string
  dislikeCount: string
  favoriteCount: string
  favorited: boolean
  aiSummary?: string | null
}

interface RawPage<T> {
  total: string
  pageNo: string
  pageSize: string
  records: T[]
}

interface RawCategoryCount {
  name: string
  count: string
}
