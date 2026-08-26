// publishing 模块 API：博客前台公开读——知识/标签（后端 xlumen-publishing review/release 域）。
// KB-3 起分类字段废弃（决策 D16 目录树接管），知识卡片改携 kbId/kbName/directoryId，查询改 kbId/directoryId 库级筛选。
// 公开读为匿名接口；评论/点赞由 engagement 模块 API 提供。
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
  /** 语义检索相关度（0~1；仅 mode=semantic 返回，缺失为 null）。 */
  semanticScore?: number | null
  /** 语义检索命中段落数（仅 mode=semantic 返回，缺失为 null）。 */
  chunkCount?: number | null
  /** 语义检索首个命中段落锚点（仅 mode=semantic 返回，缺失为 null）。 */
  firstAnchor?: string | null
}

/** 知识详情（B02；起携带点踩/收藏统计与收藏态， AI 摘要）。 */
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
  /** 检索模式：keyword=MySQL 关键词（默认）；semantic=向量语义（仅登录可用）。 */
  mode?: 'keyword' | 'semantic'
  pageNo?: number
  pageSize?: number
}

/** 还原后端 Long→String 的统计数值。 */
function toNumber(value: unknown): number {
  return Number(value ?? 0)
}

/** 分页查询公开知识（关键词/标签/知识库/目录组合筛选）。 */
export async function fetchKnowledges(
  query: KnowledgeQuery,
  signal?: AbortSignal,
): Promise<PageResult<KnowledgeCard>> {
  const { data } = await http.get<ApiResponse<RawPage<RawCard>>>('/public/knowledge', {
    params: query,
    ...(signal ? { signal } : {}),
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
      // 语义字段后端同为 Long/数值：缺失时保留 null，避免把「无相关度」显示成 0
      semanticScore: card.semanticScore == null ? null : toNumber(card.semanticScore),
      chunkCount: card.chunkCount == null ? null : toNumber(card.chunkCount),
    })),
  }
}

/** 知识详情（B02）。 */
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
    semanticScore: knowledge.semanticScore == null ? null : toNumber(knowledge.semanticScore),
    chunkCount: knowledge.chunkCount == null ? null : toNumber(knowledge.chunkCount),
  }
}

/** 阅读量上报（匿名；同访客 24 小时窗口只计一次）。 */
export async function reportView(id: string): Promise<void> {
  const { data } = await http.post<ApiResponse<boolean>>(`/public/knowledge/${id}/view`)
  unwrap(data)
}

/** 相关推荐条目（详情页底部推荐；publishedAt 可能为 null）。 */
export interface RelatedKnowledge {
  id: string
  title: string
  kbName: string
  publishedAt: string | null
}

/** 相关推荐（详情页正文下方，至多 5 条由前端截取）。 */
export async function fetchRelatedKnowledge(id: string): Promise<RelatedKnowledge[]> {
  const { data } = await http.get<ApiResponse<RelatedKnowledge[]>>(
    `/public/knowledge/${id}/related`,
  )
  return unwrap(data)
}

/** 标签聚合（B01 侧栏/B03 筛选）。 */
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
  semanticScore?: string | null
  chunkCount?: string | null
  firstAnchor?: string | null
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
