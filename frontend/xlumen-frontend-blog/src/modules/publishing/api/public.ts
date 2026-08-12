// publishing 模块 API：博客前台公开读（F-0201/F-0202）——文章/分类/标签（后端 xlumen-publishing review/release 域）。
// 公开读为匿名接口；评论/点赞（F-0203）由 engagement 模块 API 提供。
// ID 类字段为 string（雪花 ID 超出 JS 安全整数，后端 Long 序列化为 String，BACKEND.md §5.3）；
// 统计/分页数值在 API 层 Number() 还原，页面代码不感知。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 文章卡片（B01 列表）。 */
export interface ArticleCard {
  id: string
  title: string
  summary: string
  authorName: string
  category: string
  tags: string[]
  viewCount: number
  readMinutes: number
  commentCount: number
  likeCount: number
  publishedAt: string
}

/** 文章详情（B02）。 */
export interface ArticleDetail extends ArticleCard {
  content: string
  liked: boolean
  updatedAt: string
}

/** 分类/标签聚合项。 */
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

export interface ArticleQuery {
  keyword?: string
  category?: string
  tag?: string
  pageNo?: number
  pageSize?: number
}

/** 还原后端 Long→String 的统计数值。 */
function toNumber(value: unknown): number {
  return Number(value ?? 0)
}

/** 分页查询公开文章（关键词/分类/标签组合筛选，F-0201/F-0202）。 */
export async function fetchArticles(query: ArticleQuery): Promise<PageResult<ArticleCard>> {
  const { data } = await http.get<ApiResponse<RawPage<RawCard>>>('/public/articles', { params: query })
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

/** 文章详情（F-0201，B02）。 */
export async function fetchArticle(id: string): Promise<ArticleDetail> {
  const { data } = await http.get<ApiResponse<RawArticleDetail>>(`/public/articles/${id}`)
  const article = unwrap(data)
  return {
    ...article,
    viewCount: toNumber(article.viewCount),
    commentCount: toNumber(article.commentCount),
    likeCount: toNumber(article.likeCount),
    readMinutes: toNumber(article.readMinutes),
  }
}

/** 阅读量上报（F-0203，匿名；同访客 24 小时窗口只计一次）。 */
export async function reportView(id: string): Promise<void> {
  const { data } = await http.post<ApiResponse<boolean>>(`/public/articles/${id}/view`)
  unwrap(data)
}

/** 分类聚合（F-0202，B01 侧栏/B03 筛选）。 */
export async function fetchCategories(): Promise<CategoryCount[]> {
  const { data } = await http.get<ApiResponse<RawCategoryCount[]>>('/public/categories')
  return unwrap(data).map((item) => ({ ...item, count: toNumber(item.count) }))
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
  category: string
  tags: string[]
  viewCount: string
  readMinutes: string
  commentCount: string
  likeCount: string
  publishedAt: string
}

interface RawArticleDetail extends RawCard {
  content: string
  liked: boolean
  updatedAt: string
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
