import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useSessionStore } from '@/stores/session'

// 会话 Store 原子操作测试（FRONTEND.md §7：accept/clear 整体替换，不逐字段修改）
describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('establish 整体写入会话快照与令牌', () => {
    const session = useSessionStore()
    session.establish(
      { userId: 1, username: 'tester', workspaceId: 100, roles: ['OWNER'] },
      'access-token',
      'refresh-token',
    )
    expect(session.loggedIn).toBe(true)
    expect(session.accessToken).toBe('access-token')
    expect(session.refreshToken).toBe('refresh-token')
    expect(session.snapshot?.roles).toEqual(['OWNER'])
  })

  it('clear 清理会话并复位令牌', () => {
    const session = useSessionStore()
    session.establish(
      { userId: 1, username: 'tester', workspaceId: 100, roles: ['OWNER'] },
      'access-token',
      'refresh-token',
    )
    session.clear()
    expect(session.loggedIn).toBe(false)
    expect(session.accessToken).toBe('')
    expect(session.refreshToken).toBe('')
    expect(session.snapshot).toBeNull()
  })
})
