import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// 模型配置接口封装（F-0501/F-0502 管理面）：页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

/** 供应商选项（展示文案 → 供应商编码，与后端枚举对齐）。 */
export const PROVIDER_OPTIONS = [
  { value: 'BAILIAN', label: '百炼' },
  { value: 'DEEPSEEK', label: 'DeepSeek' },
] as const

export type ProviderValue = (typeof PROVIDER_OPTIONS)[number]['value']

/** 场景编码 → 展示文案（未知场景回退为原始编码）。 */
export const SCENE_LABELS: Record<string, string> = {
  WRITING: '写作',
  REVIEWER: '审核',
  QA: '问答',
  SUMMARY: '摘要',
  EMBEDDING: 'Embedding',
}

export interface ModelConfig {
  scene: string
  provider: string
  model: string
  paramsJson?: string
  updatedAt: string
}

/** 更新入参（F-0501/F-0502）。 */
export interface ModelConfigUpdate {
  provider: string
  model: string
  paramsJson?: string
}

/** 连通性测试结果。 */
export interface ModelTestResult {
  ok: boolean
  message: string
}

/** 查询全部场景模型配置。 */
export async function fetchModelConfigs(): Promise<ModelConfig[]> {
  const { data } = await http.get<ApiResponse<ModelConfig[]>>('/admin/model-configs')
  return unwrap(data)
}

/** 更新指定场景模型配置。 */
export async function updateModelConfig(
  scene: string,
  payload: ModelConfigUpdate,
): Promise<ModelConfig> {
  const { data } = await http.put<ApiResponse<ModelConfig>>(
    `/admin/model-configs/${encodeURIComponent(scene)}`,
    payload,
  )
  return unwrap(data)
}

/** 连通性测试。 */
export async function testModelConfig(provider: string, model: string): Promise<ModelTestResult> {
  const { data } = await http.post<ApiResponse<ModelTestResult>>('/admin/model-configs/test', {
    provider,
    model,
  })
  return unwrap(data)
}
