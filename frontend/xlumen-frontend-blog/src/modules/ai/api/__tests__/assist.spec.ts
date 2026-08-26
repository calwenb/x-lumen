import { beforeEach, describe, expect, it, vi } from 'vitest'

// 统一 AI 辅助端点测试：请求体透传、响应解包、失败抛错。
// http 层以 mock 替代真实 axios，不发起网络请求。
vi.mock('@/api/http', () => ({
  http: { post: vi.fn() },
  unwrap: (body: { code: string; message: string; data: unknown }) => {
    if (body.code !== 'SUCCESS') {
      throw new Error(body.message || body.code)
    }
    return body.data
  },
}))

import { assistAction, explainTerm } from '@/modules/ai/api/assist'
import { http } from '@/api/http'

const mockedPost = vi.mocked(http.post)

describe('assistAction', () => {
  beforeEach(() => {
    mockedPost.mockReset()
  })

  it('透传动作与全文并解包返回结果文本', async () => {
    mockedPost.mockResolvedValue({
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: { text: '润色后的正文' },
        requestId: 'req-1',
      },
    })
    await expect(assistAction({ action: 'polish', content: '原文' })).resolves.toBe('润色后的正文')
    expect(mockedPost).toHaveBeenCalledWith('/ai/assist', { action: 'polish', content: '原文' })
  })

  it('带可选上下文时一并透传', async () => {
    mockedPost.mockResolvedValue({
      data: { code: 'SUCCESS', message: 'ok', data: { text: '标题A' }, requestId: 'req-2' },
    })
    await expect(
      assistAction({ action: 'titles', content: '正文', selection: '选中片段', title: '原标题' }),
    ).resolves.toBe('标题A')
    expect(mockedPost).toHaveBeenCalledWith('/ai/assist', {
      action: 'titles',
      content: '正文',
      selection: '选中片段',
      title: '原标题',
    })
  })

  it('业务失败时向上抛错', async () => {
    mockedPost.mockRejectedValue(new Error('AI 服务不可用'))
    await expect(assistAction({ action: 'continue', content: 'x' })).rejects.toThrow(
      'AI 服务不可用',
    )
  })

  it('图片讲解透传 imageUrl 且不携带 content', async () => {
    mockedPost.mockResolvedValue({
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: { text: '这是一张架构图…' },
        requestId: 'req-3',
      },
    })
    await expect(
      assistAction({ action: 'image_explain', imageUrl: 'https://example.com/a.png' }),
    ).resolves.toBe('这是一张架构图…')
    expect(mockedPost).toHaveBeenCalledWith('/ai/assist', {
      action: 'image_explain',
      imageUrl: 'https://example.com/a.png',
    })
  })
})

describe('explainTerm', () => {
  beforeEach(() => {
    mockedPost.mockReset()
  })

  it('透传术语并解包返回 termsCache 等字段', async () => {
    mockedPost.mockResolvedValue({
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: {
          term: 'Bloom Filter',
          explanation: '布隆过滤器是一种概率型数据结构。',
          fromCache: true,
        },
        requestId: 'req-4',
      },
    })
    await expect(explainTerm('Bloom Filter')).resolves.toEqual({
      term: 'Bloom Filter',
      explanation: '布隆过滤器是一种概率型数据结构。',
      fromCache: true,
    })
    expect(mockedPost).toHaveBeenCalledWith('/ai/term-explain', { term: 'Bloom Filter' })
  })
})
