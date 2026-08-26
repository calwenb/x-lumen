import { beforeEach, describe, expect, it, vi } from 'vitest'

// blog 更新日志 API 测试：分页参数透传、数值还原（Long→String）、失败抛错。
// http 层以 mock 替代真实 axios，不发起网络请求。
vi.mock('@/api/http', () => ({
  http: { get: vi.fn() },
  unwrap: (body: { code: string; message: string; data: unknown }) => {
    if (body.code !== 'SUCCESS') {
      throw new Error(body.message || body.code)
    }
    return body.data
  },
}))

import { fetchChangelogs } from '@/modules/blog/api/changelog'
import { http } from '@/api/http'

const mockedGet = vi.mocked(http.get)

describe('fetchChangelogs', () => {
  beforeEach(() => {
    mockedGet.mockReset()
  })

  it('分页查询并把 id 与分页字段还原为数值', async () => {
    mockedGet.mockResolvedValue({
      data: {
        code: 'SUCCESS',
        message: 'ok',
        data: {
          total: '12',
          pageNo: '1',
          pageSize: '10',
          records: [
            {
              id: '1',
              title: '更新日志上线',
              content: '# 站点动态',
              publishedAt: '2026-08-01T00:00:00',
            },
            { id: '2', title: '草稿', content: '未发布', publishedAt: null },
          ],
        },
        requestId: 'req-1',
      },
    })
    const page = await fetchChangelogs({ pageNo: 1, pageSize: 10 })
    expect(page.total).toBe(12)
    expect(page.records[0]?.id).toBe(1)
    expect(page.records[0]?.publishedAt).toBe('2026-08-01T00:00:00')
    expect(page.records[1]?.publishedAt).toBeNull()
    expect(mockedGet).toHaveBeenCalledWith('/public/changelogs', {
      params: { pageNo: 1, pageSize: 10 },
    })
  })

  it('业务失败时向上抛错', async () => {
    mockedGet.mockRejectedValue(new Error('服务繁忙'))
    await expect(fetchChangelogs()).rejects.toThrow('服务繁忙')
  })
})
