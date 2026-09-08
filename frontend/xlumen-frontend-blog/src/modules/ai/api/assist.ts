// ai 模块 API：交互式文本辅助（编辑辅助 + 代码解读共用统一端点 /ai/assist）。
// 非流式接口：成功后一次性返回结果文本；失败 4xx/5xx 由 http 层抛错，页面统一提示。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 交互式 AI 辅助动作。 */
export type AssistAction =
  | 'continue'
  | 'polish'
  | 'titles'
  | 'spellfix'
  | 'code_explain'
  | 'code_bug'
  | 'code_test'
  /** 知识地图聚类：content 为「序号. 标题——摘要」多行文本，返回主题 JSON 数组。 */
  | 'kb_cluster'
  | 'kb_insight'
  /** 图片讲解：imageUrl 为绝对图片地址（http(s)），返回讲解 Markdown 文本。 */
  | 'image_explain'

/** 辅助请求：content 为全文/代码文本，selection 与 title 为可选上下文，imageUrl 供图片讲解。 */
export interface AssistRequest {
  action: AssistAction
  content?: string
  selection?: string
  title?: string
  imageUrl?: string
}

/** 辅助响应：text 为生成结果（titles 为多行标题，其余为单段文本）。 */
export interface AssistResult {
  text: string
}

/** 调用统一 AI 辅助端点，返回结果文本（调用方负责解析多行标题或直接替换）。超时依赖 http 全局 120s。 */
export async function assistAction(body: AssistRequest): Promise<string> {
  const { data } = await http.post<ApiResponse<AssistResult>>('/ai/assist', body)
  const result = unwrap(data)
  return result?.text ?? ''
}

/** 术语解释响应（公开端点，无需登录）。 */
export interface TermExplainResult {
  term: string
  explanation: string
  /** 是否命中服务端缓存（旧格式 aiSummary 无此字段时按 false 处理）。 */
  fromCache: boolean
}

/** 术语解释：选区词 ≤ 30 字悬浮触发，返回 1-2 句解释。 */
export async function explainTerm(term: string): Promise<TermExplainResult> {
  const { data } = await http.post<ApiResponse<TermExplainResult>>('/ai/term-explain', { term })
  return unwrap(data)
}
