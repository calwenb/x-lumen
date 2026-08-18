// engagement 模块 API：知识互动（F-0203/F-0212/F-0213）--评论列表匿名可读，
// 发表评论/知识点赞点踩收藏/评论点赞点踩/收藏列表需登录（后端 xlumen-publishing engagement 域；
// 401 由统一拦截器单飞刷新处理）。
// ID 类字段为 string（雪花 ID 超出 JS 安全整数，后端 Long 序列化为 String，BACKEND.md §5.3）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

import type { KnowledgeCard, PageResult } from '@/modules/publishing/api/public'

/** 互动反应状态（F-0212 契约）：LIKE=已赞 / DISLIKE=已踩 / NONE=已取消（toggle 后无反应）。 */
export type Reaction = 'LIKE' | 'DISLIKE' | 'NONE'

/** toggle 类接口响应体：以服务端返回的 reaction 为准（赞/踩互斥，重复点击取消）。 */
export interface ReactionResult {
  reaction: Reaction
}

/** 评论视图（F-0213 起携带点赞/点踩计数与当前用户反应）。 */
export interface CommentItem {
  id: string
  knowledgeId: string
  parentId: string | null
  userName: string
  content: string
  likeCount: number
  dislikeCount: number
  myReaction: 'LIKE' | 'DISLIKE' | null
  createdAt: string
}

/** 收藏卡片（F-0212，B23）：KnowledgeCard 字段 + 收藏时间。 */
export interface FavoriteItem extends KnowledgeCard {
  favoritedAt: string
}

/** 还原后端 Long->String 的统计数值（与 publishing/api/public 同一约定）。 */
function toNumber(value: unknown): number {
  return Number(value ?? 0)
}

/** 知识互动 URL：/public/knowledge/{id}[/action]。 */
function knowledgeUrl(knowledgeId: string, action = ''): string {
  return '/public/knowledge/' + knowledgeId + action
}

/** 评论互动 URL：/public/comments/{id}/like|dislike。 */
function commentUrl(commentId: string, action: string): string {
  return '/public/comments/' + commentId + action
}

/** 评论列表（匿名可读，F-0203；计数与当前用户反应由 F-0213 起返回）。 */
export async function fetchComments(
  knowledgeId: string,
  pageNo = 1,
  pageSize = 50,
): Promise<PageResult<CommentItem>> {
  const { data } = await http.get<ApiResponse<PageResult<CommentItem>>>(knowledgeUrl(knowledgeId, '/comments'), {
    params: { pageNo, pageSize },
  })
  const page = unwrap(data)
  return {
    total: Number(page.total),
    pageNo: Number(page.pageNo),
    pageSize: Number(page.pageSize),
    records: page.records.map((comment) => ({
      ...comment,
      id: String(comment.id),
      knowledgeId: String(comment.knowledgeId),
      parentId: comment.parentId == null ? null : String(comment.parentId),
      likeCount: toNumber(comment.likeCount),
      dislikeCount: toNumber(comment.dislikeCount),
      myReaction: comment.myReaction ?? null,
    })),
  }
}

/** 发表评论（需登录，F-0203）。 */
export async function createComment(knowledgeId: string, content: string): Promise<CommentItem> {
  const { data } = await http.post<ApiResponse<CommentItem>>(knowledgeUrl(knowledgeId, '/comments'), {
    content,
  })
  const comment = unwrap(data)
  return {
    ...comment,
    id: String(comment.id),
    knowledgeId: String(comment.knowledgeId),
    parentId: comment.parentId == null ? null : String(comment.parentId),
    likeCount: toNumber(comment.likeCount),
    dislikeCount: toNumber(comment.dislikeCount),
    myReaction: comment.myReaction ?? null,
  }
}

/** 点赞 toggle（需登录，F-0203/F-0212）：与点踩互斥，返回切换后的反应状态。 */
export async function toggleLike(knowledgeId: string): Promise<ReactionResult> {
  const { data } = await http.post<ApiResponse<ReactionResult>>(knowledgeUrl(knowledgeId, '/like'))
  return unwrap(data)
}

/** 点踩 toggle（需登录，F-0212）：与点赞互斥，返回切换后的反应状态。 */
export async function toggleDislike(knowledgeId: string): Promise<ReactionResult> {
  const { data } = await http.post<ApiResponse<ReactionResult>>(knowledgeUrl(knowledgeId, '/dislike'))
  return unwrap(data)
}

/** 收藏 toggle（需登录，F-0212）：返回 true=已收藏 / false=已取消。 */
export async function toggleFavorite(knowledgeId: string): Promise<boolean> {
  const { data } = await http.post<ApiResponse<boolean>>(knowledgeUrl(knowledgeId, '/favorite'))
  return unwrap(data)
}

/** 我的收藏列表（需登录，F-0212，B23）。 */
export async function fetchFavorites(pageNo = 1, pageSize = 10): Promise<PageResult<FavoriteItem>> {
  const { data } = await http.get<ApiResponse<PageResult<FavoriteItem>>>('/public/favorites', {
    params: { pageNo, pageSize },
  })
  const page = unwrap(data)
  return {
    total: toNumber(page.total),
    pageNo: toNumber(page.pageNo),
    pageSize: toNumber(page.pageSize),
    records: page.records.map((item) => ({
      ...item,
      id: String(item.id),
      viewCount: toNumber(item.viewCount),
      commentCount: toNumber(item.commentCount),
      likeCount: toNumber(item.likeCount),
      readMinutes: toNumber(item.readMinutes),
    })),
  }
}

/** 评论点赞 toggle（需登录，F-0213）：与点踩互斥，返回切换后的反应状态。 */
export async function toggleCommentLike(commentId: string): Promise<ReactionResult> {
  const { data } = await http.post<ApiResponse<ReactionResult>>(commentUrl(commentId, '/like'))
  return unwrap(data)
}

/** 评论点踩 toggle（需登录，F-0213）：与点赞互斥，返回切换后的反应状态。 */
export async function toggleCommentDislike(commentId: string): Promise<ReactionResult> {
  const { data } = await http.post<ApiResponse<ReactionResult>>(commentUrl(commentId, '/dislike'))
  return unwrap(data)
}
