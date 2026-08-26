// chat 模块 API：访客（未登录）流式对话——POST /api/v1/public/chat。
// 无需登录即可使用：内部复用 ai/utils/sse.ts 的 streamSse（fetch + ReadableStream 解析），
// 事件与登录态 chat/stream 一致（chunk/citation/followups/done/error），便于面板按能力选用。
import type { SseEvent } from '@/modules/ai/utils/sse'
import { streamSse } from '@/modules/ai/utils/sse'

import { parseCitations, parseFollowups } from '@/modules/chat/api/chat'
import { SseEventName } from '@/modules/ai/utils/sseEvent'

import type { Citation } from '@/modules/chat/api/chat'

/** 访客流式对话回调（能力子集：无会话、无工具轨迹）。 */
export interface PublicChatCallbacks {
  onChunk: (text: string) => void
  onCitations: (citations: Citation[]) => void
  onFollowups?: (questions: string[]) => void
  onDone: () => void
}

/** 访客流式对话：单次问答，不保留会话。 */
export function streamPublicChat(
  query: string,
  callbacks: PublicChatCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  return streamSse(
    '/public/chat',
    { method: 'POST', body: { query }, ...(signal ? { signal } : {}) },
    (event: SseEvent) => {
      switch (event.event) {
        case SseEventName.chunk:
          callbacks.onChunk(event.data)
          break
        case SseEventName.citation:
          callbacks.onCitations(parseCitations(event.data))
          break
        case SseEventName.followups:
          callbacks.onFollowups?.(parseFollowups(event.data))
          break
        case SseEventName.done:
          callbacks.onDone()
          break
        case SseEventName.error:
          throw new Error(event.data)
      }
    },
  )
}
