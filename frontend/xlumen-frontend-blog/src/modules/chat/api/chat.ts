// chat 模块 API：AI 助理对话（F-0701，B00/D01）+ 知识级问答（F-0702，D02）。
// 流式对话复用 ai/utils/sse.ts 的 fetch 解析（需 Authorization 头）；REST 走统一 http 客户端。
// ID 为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'
import type { SseEvent } from '@/modules/ai/utils/sse'
import { streamSse } from '@/modules/ai/utils/sse'

/** 会话。 */
export interface Conversation {
  id: string
  title: string
  updatedAt: string
}

/** 消息（citations 已由 citationsJson 解析）。 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
}

/** 引用溯源。 */
export interface Citation {
  knowledgeId: string
  title: string
  chunkSeq: number
  headingAnchor: string
  chunkText: string
  score: number
}

/** 流式对话回调。 */
export interface ChatStreamCallbacks {
  onChunk: (text: string) => void
  onCitations: (citations: Citation[]) => void
  onDone: (result: { conversationId: string; messageId: string }) => void
}

interface RawConversation {
  id: string
  title: string
  updatedAt: string
}

interface RawMessage {
  id: string
  role: string
  content: string
  citationsJson: string | null
}

/** 会话列表（登录可见，F-0701）。 */
export async function fetchConversations(): Promise<Conversation[]> {
  const { data } = await http.get<ApiResponse<RawConversation[]>>('/chat/conversations')
  return unwrap(data).map((item) => ({
    id: String(item.id),
    title: item.title ?? '',
    updatedAt: item.updatedAt ?? '',
  }))
}

/** 会话消息历史（F-0701）。 */
export async function fetchMessages(conversationId: string): Promise<ChatMessage[]> {
  const { data } = await http.get<ApiResponse<RawMessage[]>>(`/chat/conversations/${conversationId}/messages`)
  return unwrap(data).map((message) => ({
    id: String(message.id),
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content ?? '',
    citations: parseCitations(message.citationsJson ?? ''),
  }))
}

/** 新建会话（F-0701）：后端 data 直接返回 id 字符串（Long 全局序列化为 String）。 */
export async function createConversation(title: string): Promise<{ id: string }> {
  const { data } = await http.post<ApiResponse<string>>('/chat/conversations', { title })
  return { id: String(unwrap(data)) }
}

/** 检索范围（KB-3，决策 D13）：kbId 限定单库；allVisible 是否检索全部可见库（默认 true）。 */
export interface ChatScope {
  /** 限定检索的知识库 ID（可空；空=按 allVisible 决定范围）。 */
  kbId?: string
  /** 是否检索全部可见库（可空=true）。 */
  allVisible?: boolean
}

/** 流式对话（F-0701）：chunk 文本增量 / citation 引用 / done 会话归属。 */
export function streamChat(
  body: { query: string; conversationId?: string } & ChatScope,
  callbacks: ChatStreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  return runChatStream(
    '/chat/stream',
    {
      query: body.query,
      ...(body.conversationId ? { conversationId: body.conversationId } : {}),
      ...(body.kbId ? { kbId: body.kbId } : {}),
      ...(body.allVisible !== undefined ? { allVisible: body.allVisible } : {}),
    },
    callbacks,
    signal,
  )
}

/** 知识级流式问答（F-0702，D02）：scope 空=全部可见库；传 kbId=锁定当前知识库。 */
export function streamKnowledgeAsk(
  knowledgeId: string,
  query: string,
  callbacks: ChatStreamCallbacks,
  signal?: AbortSignal,
  scope?: ChatScope,
): Promise<void> {
  return runChatStream(
    `/chat/knowledge/${knowledgeId}/ask`,
    { query, ...(scope?.kbId ? { kbId: scope.kbId } : {}), ...(scope?.allVisible !== undefined ? { allVisible: scope.allVisible } : {}) },
    callbacks,
    signal,
  )
}

/** 解析引用 JSON 字符串（容错：非法 JSON 返回空数组）。 */
export function parseCitations(json: string): Citation[] {
  try {
    const parsed: unknown = JSON.parse(json)
    if (!Array.isArray(parsed)) return []
    const citations: Citation[] = []
    for (const raw of parsed) {
      const item = raw as Record<string, unknown>
      citations.push({
        knowledgeId: typeof item.knowledgeId === 'string' ? item.knowledgeId : '',
        title: typeof item.title === 'string' ? item.title : '',
        chunkSeq: Number(item.chunkSeq ?? 0),
        headingAnchor: typeof item.headingAnchor === 'string' ? item.headingAnchor : '',
        chunkText: typeof item.chunkText === 'string' ? item.chunkText : '',
        score: Number(item.score ?? 0),
      })
    }
    return citations
  } catch {
    return []
  }
}

async function runChatStream(
  path: string,
  body: Record<string, unknown>,
  callbacks: ChatStreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  await streamSse(
    path,
    { method: 'POST', body, ...(signal ? { signal } : {}) },
    (event: SseEvent) => {
      switch (event.event) {
        case 'chunk':
          callbacks.onChunk(event.data)
          break
        case 'citation':
          callbacks.onCitations(parseCitations(event.data))
          break
        case 'done': {
          const parsed = JSON.parse(event.data) as { conversationId?: string; messageId?: string }
          callbacks.onDone({
            conversationId: parsed.conversationId ?? '',
            messageId: parsed.messageId ?? '',
          })
          break
        }
        case 'error':
          throw new Error(event.data)
      }
    },
  )
}
