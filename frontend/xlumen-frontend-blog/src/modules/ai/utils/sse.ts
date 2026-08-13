// ai 模块：SSE 流式解析（fetch + ReadableStream，F-0601/F-0701）。
// 事件流需要 Authorization 头，不能用浏览器原生 EventSource；用 fetch 读取 response.body，
// 按 \n\n 分帧，逐行解析 event: 与 data: 字段后回调（多 data 行以 \n 连接，首空格剥离）。
import { useSessionStore } from '@/stores/session'
import { createRequestId } from '@/utils/request-id'

/** SSE 事件：event 为事件名（chunk/citation/done/error 等），data 为原始负载文本。 */
export interface SseEvent {
  event: string
  data: string
}

/** 流式请求选项。 */
export interface SseRequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  signal?: AbortSignal
}

/**
 * 通过 fetch 读取 SSE 流并逐帧回调。
 *
 * @param path 以 /api/v1 开头的接口路径
 * @param options 请求选项（method/body/signal）
 * @param onEvent 事件回调；在回调内抛出错误会中止读取并向上传播
 */
export async function streamSse(
  path: string,
  options: SseRequestOptions,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const session = useSessionStore()
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'X-Request-Id': createRequestId(),
  }
  if (session.accessToken) {
    headers.Authorization = `Bearer ${session.accessToken}`
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`/api/v1${path}`, {
    method: options.method ?? 'GET',
    headers,
    ...(options.signal ? { signal: options.signal } : {}),
    ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
  })

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const drain = (): void => {
    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const frame = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      dispatchFrame(frame, onEvent)
      boundary = buffer.indexOf('\n\n')
    }
  }

  try {
    let chunk = await reader.read()
    while (!chunk.done) {
      buffer += decoder.decode(chunk.value, { stream: true })
      drain()
      chunk = await reader.read()
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      dispatchFrame(buffer.trim(), onEvent)
    }
  } finally {
    reader.releaseLock()
  }
}

/** 解析单帧：event: 行决定事件名，data: 行拼接为负载。 */
function dispatchFrame(frame: string, onEvent: (event: SseEvent) => void): void {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).replace(/^ /, ''))
    }
  }
  if (dataLines.length === 0) return
  onEvent({ event: eventName, data: dataLines.join('\n') })
}

/** 非 2xx 时读取统一响应结构中的 message 作为错误信息。 */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body) {
      const message = (body as { message?: unknown }).message
      if (typeof message === 'string' && message) return message
    }
  } catch {
    // 非 JSON 响应，回退到状态码提示
  }
  return `请求失败（${response.status}）`
}
