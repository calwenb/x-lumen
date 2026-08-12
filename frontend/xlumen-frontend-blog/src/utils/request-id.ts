/**
 * 请求 ID 生成：前端为所有请求附带 X-Request-Id（FRONTEND.md §8.1）。
 * 使用原生 crypto.randomUUID，无外部依赖。
 */
export function createRequestId(): string {
  return crypto.randomUUID()
}
