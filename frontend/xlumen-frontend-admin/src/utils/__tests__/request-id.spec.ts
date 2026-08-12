import { describe, expect, it } from 'vitest'

import { createRequestId } from '@/utils/request-id'

// 请求 ID 生成单元测试（FRONTEND.md §14：纯函数重点覆盖）
describe('createRequestId', () => {
  it('生成符合 UUID 格式的请求 ID', () => {
    expect(createRequestId()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    )
  })

  it('两次生成的请求 ID 不重复', () => {
    expect(createRequestId()).not.toBe(createRequestId())
  })
})
