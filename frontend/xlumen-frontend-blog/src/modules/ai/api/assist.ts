// ai 模块 API：交互式文本辅助（编辑辅助 + 代码解读共用统一端点 /ai/assist）。
// 非流式接口：成功后一次性返回结果文本；失败 4xx/5xx 由 http 层抛错，页面统一提示。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 交互式 AI 辅助动作。 */
export type AssistAction =
  'continue' | 'polish' | 'titles' | 'spellfix' | 'code_explain' | 'code_bug' | 'code_test'

/** 辅助请求：content 为全文/代码文本，selection 与 title 为可选上下文。 */
export interface AssistRequest {
  action: AssistAction
  content: string
  selection?: string
  title?: string
}

/** 辅助响应：text 为生成结果（titles 为多行标题，其余为单段文本）。 */
export interface AssistResult {
  text: string
}

/** 调用统一 AI 辅助端点，返回结果文本（调用方负责解析多行标题或直接替换）。 */
export async function assistAction(body: AssistRequest): Promise<string> {
  const { data } = await http.post<ApiResponse<AssistResult>>('/ai/assist', body)
  const result = unwrap(data)
  return result?.text ?? ''
}
