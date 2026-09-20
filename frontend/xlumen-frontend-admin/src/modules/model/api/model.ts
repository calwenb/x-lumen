import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// 模型配置接口封装：页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

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
  SEO: 'SEO',
}

export interface ModelConfig {
  scene: string
  /** 覆盖供应商（ai_scene_config 行；无覆盖时后端返回 null，页面加载时归一化为空串）。 */
  provider: string
  /** 覆盖模型（无覆盖时后端返回 null，页面加载时归一化为空串）。 */
  model: string
  paramsJson?: string
  /** 场景 Prompt（WRITING 场景为 JSON 文本；空字符串表示默认）。 */
  prompt?: string
  /** 每日配额：0 表示不限。 */
  dailyQuota: number
  /** 覆盖行更新时间；无覆盖行时为空。 */
  updatedAt: string
  /** 运行时生效的供应商（数据库覆盖优先，否则环境默认）。 */
  effectiveProvider: string
  /** 运行时生效的模型（数据库覆盖优先，否则环境默认）。 */
  effectiveModel: string
  /** 生效来源：DB=数据库覆盖，ENV=环境默认（profile YAML）。 */
  source: 'DB' | 'ENV'
}

/** 更新入参（prompt/dailyQuota 缺省或 null 时后端保持原值）。 */
export interface ModelConfigUpdate {
  provider: string
  model: string
  paramsJson?: string
  prompt?: string
  dailyQuota?: number
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

/** 连通性测试：同步调用真实模型，超时依赖 http 全局 120s。 */
export async function testModelConfig(provider: string, model: string): Promise<ModelTestResult> {
  const { data } = await http.post<ApiResponse<ModelTestResult>>('/admin/model-configs/test', {
    provider,
    model,
  })
  return unwrap(data)
}
