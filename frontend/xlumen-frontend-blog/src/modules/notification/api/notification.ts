// notification 模块 API：站内消息，对应后端 /api/v1/notifications）。
// 消息与业务解耦：当前 REVIEW（AI 审核结果）事件消费 ai_task 完结；评论回复/@小光等后续复用。
// ID/缩略信息为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'
import type { SseEvent } from '@/modules/ai/utils/sse'
import { streamSse } from '@/modules/ai/utils/sse'

/** 站内消息。 */
export interface NotificationItem {
  id: string
  type: string
  title: string
  content: string
  link: string
  read: boolean
  createdAt: string
}

/** 消息分页结果（附带未读数，一次取齐铃铛角标）。 */
export interface NotificationPage {
  total: number
  records: NotificationItem[]
  unreadCount: number
}

interface RawNotification {
  id: string
  type: string
  title: string
  content: string
  link: string
  read: boolean
  createdAt: string
}

interface RawNotificationPage {
  total: number
  records: RawNotification[]
  unreadCount: number
}

/** 消息列表（时间倒序）。 */
export async function fetchNotifications(pageNo = 1, pageSize = 20): Promise<NotificationPage> {
  const { data } = await http.get<ApiResponse<RawNotificationPage>>('/notifications', {
    params: { pageNo, pageSize },
  })
  const page = unwrap(data)
  return {
    total: page.total ?? 0,
    unreadCount: page.unreadCount ?? 0,
    records: (page.records ?? []).map(normalize),
  }
}

/** 未读数量（铃铛角标轮询）。 */
export async function fetchUnreadCount(): Promise<number> {
  const { data } = await http.get<ApiResponse<number>>('/notifications/unread-count')
  return Number(unwrap(data) ?? 0)
}

/** 标记单条已读。 */
export async function markNotificationRead(id: string): Promise<void> {
  const { data } = await http.post<ApiResponse<void>>(`/notifications/${id}/read`)
  unwrap(data)
}

/** 全部标记已读。 */
export async function markAllNotificationsRead(): Promise<void> {
  const { data } = await http.post<ApiResponse<void>>('/notifications/read-all')
  unwrap(data)
}

/**
 * 实时推送流（SSE）：后端通知创建时推送「notification」事件，无需 WS）。
 * 断线/结束返回后由调用方决定重连；返回的 AbortController 供卸载时断开。
 */
export function streamNotifications(
  onNotification: (item: NotificationItem) => void,
  signal?: AbortSignal,
): Promise<void> {
  return streamSse(
    '/notifications/stream',
    { method: 'GET', ...(signal ? { signal } : {}) },
    (event: SseEvent) => {
      if (event.event !== 'notification') return
      try {
        const raw = JSON.parse(event.data) as RawNotification
        onNotification(normalize(raw))
      } catch {
        // 单条推送解析失败忽略
      }
    },
  )
}

function normalize(raw: RawNotification): NotificationItem {
  return {
    id: String(raw.id),
    type: raw.type ?? '',
    title: raw.title ?? '',
    content: raw.content ?? '',
    link: raw.link ?? '',
    read: raw.read === true,
    createdAt: raw.createdAt ?? '',
  }
}
