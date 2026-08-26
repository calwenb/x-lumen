import { http, unwrap } from '@/api/http'
import type { ApiResponse } from '@/api/types'

export interface ChangelogItem {
  id: string
  title: string
  content: string
  published: boolean
  publishedAt: string | null
  updatedAt: string | null
}

export interface ChangelogPage {
  records: ChangelogItem[]
  total: number
}

export interface ChangelogPayload {
  title: string
  content: string
  published: boolean
}

export async function fetchChangelogs(pageNo = 1, pageSize = 20): Promise<ChangelogPage> {
  const { data } = await http.get<ApiResponse<ChangelogPage>>('/admin/changelogs', {
    params: { pageNo, pageSize },
  })
  return unwrap(data)
}

export async function createChangelog(payload: ChangelogPayload): Promise<void> {
  await http.post<ApiResponse<void>>('/admin/changelogs', payload)
}

export async function updateChangelog(id: string, payload: ChangelogPayload): Promise<void> {
  await http.put<ApiResponse<void>>(`/admin/changelogs/${id}`, payload)
}

export async function deleteChangelog(id: string): Promise<void> {
  await http.delete<ApiResponse<void>>(`/admin/changelogs/${id}`)
}