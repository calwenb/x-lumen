// ai-enhance 模块 API：AI 增值（F-0801/F-0802，M09）——摘要/SEO 生成。
// 返回 resultJson 为 JSON 字符串：SUMMARY → {"summary"}；SEO → {"title","keywords","description"}。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

export type EnhanceScene = 'SUMMARY' | 'SEO'

export interface EnhanceRequest {
  knowledgeId?: string
  scene: EnhanceScene
  content: string
}

export interface EnhanceResult {
  scene: EnhanceScene
  resultJson: string
}

/** 生成摘要/SEO（F-0801/F-0802）。 */
export async function enhanceKnowledge(payload: EnhanceRequest): Promise<EnhanceResult> {
  const { data } = await http.post<ApiResponse<EnhanceResult>>('/ai/enhance', payload)
  const result = unwrap(data)
  return {
    scene: result.scene === 'SEO' ? 'SEO' : 'SUMMARY',
    resultJson: result.resultJson ?? '',
  }
}

/** 解析 resultJson 为字段映射（数组字段（关键词）用「，」连接）。 */
export function parseEnhanceResult(resultJson: string): Record<string, string> {
  try {
    const parsed: unknown = JSON.parse(resultJson)
    if (!parsed || typeof parsed !== 'object') return {}
    const record = parsed as Record<string, unknown>
    const out: Record<string, string> = {}
    for (const [key, value] of Object.entries(record)) {
      if (Array.isArray(value)) {
        out[key] = value.filter((item): item is string => typeof item === 'string').join('，')
      } else if (typeof value === 'string') {
        out[key] = value
      }
    }
    return out
  } catch {
    return {}
  }
}
