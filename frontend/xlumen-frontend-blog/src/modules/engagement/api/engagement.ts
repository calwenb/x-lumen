// engagement 模块 API：知识互动（F-0203）——评论列表匿名可读，发表评论与点赞需登录
// （后端 xlumen-publishing engagement 域；401 由统一拦截器单飞刷新处理）。
// ID 类字段为 string（雪花 ID 超出 JS 安全整数，后端 Long 序列化为 String，BACKEND.md §5.3）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

import type { PageResult } from '@/modules/publishing/api/public'

/** 评论视图。 */
export interface CommentItem {
  id: string
  knowledgeId: string
  parentId: string | null
  userName: string
  content: string
  createdAt: string
}

/** 评论列表（匿名可读，F-0203）。 */
export async function fetchComments(knowledgeId: string, pageNo = 1, pageSize = 50): Promise<PageResult<CommentItem>> {
  const { data } = await http.get<ApiResponse<PageResult<CommentItem>>>(
    `/public/knowledge/${knowledgeId}/comments`,
    { params: { pageNo, pageSize } },
  )
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
    })),
  }
}

/** 发表评论（需登录，F-0203）。 */
export async function createComment(knowledgeId: string, content: string): Promise<CommentItem> {
  const { data } = await http.post<ApiResponse<CommentItem>>(`/public/knowledge/${knowledgeId}/comments`, { content })
  const comment = unwrap(data)
  return {
    ...comment,
    id: String(comment.id),
    knowledgeId: String(comment.knowledgeId),
    parentId: comment.parentId == null ? null : String(comment.parentId),
  }
}

/** 点赞/取消点赞切换（需登录，F-0203）；返回切换后的状态。 */
export async function toggleLike(knowledgeId: string): Promise<boolean> {
  const { data } = await http.post<ApiResponse<boolean>>(`/public/knowledge/${knowledgeId}/like`)
  return unwrap(data)
}
