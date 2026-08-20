// publishing 模块 API：审核中心（F-0902/F-0904，B12，对应后端 xlumen-publishing review 域）。
// ID/版本为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）；
// 分页数值在 API 层 Number() 还原，页面代码不感知。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'
import type { PageResult } from '@/modules/publishing/api/public'

/** 审核状态。 */
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** AI 审校问题（aiResultJson 解析后）。 */
export interface ReviewIssue {
  severity: 'error' | 'warning' | 'info'
  position: string
  evidence: string
  suggestion: string
}

/** 审核视图。 */
export interface ReviewVO {
  id: string
  knowledgeId: string
  knowledgeTitle: string
  version: string
  status: string
  aiTaskId: string
  aiResultJson: string
  aiTaskStatus: string
  autoDecision: 'REVIEWING' | 'READY' | 'BLOCKED' | 'FAILED' | 'PUBLISHED' | ''
  aiErrorMessage: string
  rejectReason: string
  rejectPosition: string
  rejectExpectation: string
  createdAt: string
  updatedAt: string
}

/** 审核查询参数。 */
export interface ReviewQuery {
  status?: ReviewStatus
  pageNo: number
  pageSize: number
}

/** 驳回入参。 */
export interface RejectPayload {
  version: string
  reason: string
  position: string
  expectation: string
}

/** 后端原始形态（可空字段归一化为空串）。 */
interface RawReview {
  id: string
  knowledgeId: string
  knowledgeTitle: string
  version: string
  status: string
  aiTaskId: string | null
  aiResultJson: string | null
  aiTaskStatus: string | null
  autoDecision: string | null
  aiErrorMessage: string | null
  rejectReason: string | null
  rejectPosition: string | null
  rejectExpectation: string | null
  createdAt: string
  updatedAt: string
}

function normalizeReview(raw: RawReview): ReviewVO {
  return {
    id: String(raw.id),
    knowledgeId: String(raw.knowledgeId),
    knowledgeTitle: raw.knowledgeTitle ?? '',
    version: String(raw.version ?? ''),
    status: raw.status ?? 'PENDING',
    aiTaskId: raw.aiTaskId ?? '',
    aiResultJson: raw.aiResultJson ?? '',
    aiTaskStatus: raw.aiTaskStatus ?? '',
    autoDecision: (raw.autoDecision ?? '') as ReviewVO['autoDecision'],
    aiErrorMessage: raw.aiErrorMessage ?? '',
    rejectReason: raw.rejectReason ?? '',
    rejectPosition: raw.rejectPosition ?? '',
    rejectExpectation: raw.rejectExpectation ?? '',
    createdAt: raw.createdAt ?? '',
    updatedAt: raw.updatedAt ?? '',
  }
}

/** 提交审核（F-0902）。 */
export async function createReview(knowledgeId: string): Promise<ReviewVO> {
  const { data } = await http.post<ApiResponse<RawReview>>('/reviews', { knowledgeId })
  return normalizeReview(unwrap(data))
}

/** 新发布链路：始终执行 AI 审核。 */
export async function createAutoReview(knowledgeId: string): Promise<ReviewVO> {
  const { data } = await http.post<ApiResponse<RawReview>>('/reviews/auto', { knowledgeId })
  return normalizeReview(unwrap(data))
}

/** AI 无 error 后发布；publishAt 为空表示立即发布。 */
export async function publishAfterAutoReview(
  reviewId: string,
  publishAt?: string,
): Promise<{ id: string; status: string }> {
  const { data } = await http.post<ApiResponse<{ id: string; status: string }>>(
    `/reviews/${reviewId}/publish`,
    publishAt ? { publishAt } : {},
  )
  const result = unwrap(data)
  return { id: String(result.id), status: result.status }
}

/** 分页查询审核列表（F-0904）。 */
export async function fetchReviews(query: ReviewQuery): Promise<PageResult<ReviewVO>> {
  const { data } = await http.get<ApiResponse<RawReviewPage>>('/reviews', { params: query })
  const page = unwrap(data)
  return {
    total: Number(page.total),
    pageNo: Number(page.pageNo),
    pageSize: Number(page.pageSize),
    records: page.records.map(normalizeReview),
  }
}

/** 审核详情（F-0904）。 */
export async function fetchReview(id: string): Promise<ReviewVO> {
  const { data } = await http.get<ApiResponse<RawReview>>(`/reviews/${id}`)
  return normalizeReview(unwrap(data))
}

/** 审核通过（F-0904，乐观锁版本校验，冲突 409）。 */
export async function approveReview(id: string, version: string): Promise<void> {
  await http.post<ApiResponse<unknown>>(`/reviews/${id}/approve`, { version })
}

/** 审核驳回（F-0904，三要素必填，冲突 409）。 */
export async function rejectReview(id: string, payload: RejectPayload): Promise<void> {
  await http.post<ApiResponse<unknown>>(`/reviews/${id}/reject`, payload)
}

/** 解析 AI 审校结果 JSON 字符串为问题列表（容错：非法 JSON 返回空数组）。 */
export function parseReviewIssues(aiResultJson: string): ReviewIssue[] {
  if (!aiResultJson) return []
  try {
    const parsed: unknown = JSON.parse(aiResultJson)
    if (!Array.isArray(parsed)) return []
    const issues: ReviewIssue[] = []
    for (const raw of parsed) {
      const item = raw as Record<string, unknown>
      issues.push({
        severity: toSeverity(item.severity),
        position: typeof item.position === 'string' ? item.position : '',
        evidence: typeof item.evidence === 'string' ? item.evidence : '',
        suggestion: typeof item.suggestion === 'string' ? item.suggestion : '',
      })
    }
    return issues
  } catch {
    return []
  }
}

function toSeverity(value: unknown): ReviewIssue['severity'] {
  return value === 'error' || value === 'warning' ? value : 'info'
}

interface RawReviewPage {
  total: string
  pageNo: string
  pageSize: string
  records: RawReview[]
}
