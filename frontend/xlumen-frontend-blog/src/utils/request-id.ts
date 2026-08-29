/**
 * 请求 ID 生成：前端为所有请求附带 X-Request-Id（FRONTEND.md §8.1）。
 * 优先使用原生 crypto.randomUUID（仅安全上下文 https/localhost 可用）；
 * 在 http://IP 等非安全上下文下降级为纯 JS 实现，保证请求能正常发出。
 */
export function createRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // UUID v4 降级实现（非安全上下文：http://IP 访问时）
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}
