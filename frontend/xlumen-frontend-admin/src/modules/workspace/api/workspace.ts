import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// 空间设置接口封装（F-1201）：页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

export interface WorkspaceSettings {
  workspaceId: string
  name: string
  slug: string
  intro: string
  forceReview: boolean
}

/** 更新入参（F-1201）：仅 intro 与 forceReview 可写。 */
export interface WorkspaceSettingsUpdate {
  intro: string
  forceReview: boolean
}

/** 查询空间设置。 */
export async function fetchWorkspaceSettings(): Promise<WorkspaceSettings> {
  const { data } = await http.get<ApiResponse<WorkspaceSettings>>('/admin/workspace/settings')
  return unwrap(data)
}

/** 更新空间设置。 */
export async function updateWorkspaceSettings(
  payload: WorkspaceSettingsUpdate,
): Promise<WorkspaceSettings> {
  const { data } = await http.put<ApiResponse<WorkspaceSettings>>(
    '/admin/workspace/settings',
    payload,
  )
  return unwrap(data)
}
