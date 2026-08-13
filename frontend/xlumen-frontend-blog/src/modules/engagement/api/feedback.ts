// engagement 模块 API：读者纠错（F-1001，M11）——匿名提交，成功后返回追踪编号。
// 公开匿名接口，无需登录（后端 xlumen-publishing engagement 域）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 纠错入参（问题必填，位置/证据选填）。 */
export interface FeedbackRequest {
  position?: string
  problem: string
  evidence?: string
}

/** 提交结果：追踪编号 + 问题描述等。 */
export interface FeedbackResult {
  trackNo: string
  problem: string
}

/** 提交读者纠错（F-1001，匿名可提交）。 */
export async function submitFeedback(articleId: string, payload: FeedbackRequest): Promise<FeedbackResult> {
  const { data } = await http.post<ApiResponse<FeedbackResult>>(`/public/articles/${articleId}/feedback`, payload)
  const result = unwrap(data)
  return {
    trackNo: String(result.trackNo ?? ''),
    problem: result.problem ?? '',
  }
}
