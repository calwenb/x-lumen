import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

// 全局会话 Store（FRONTEND.md §7）：仅提供 accept()/clear()/setTokens()/establish() 原子操作，
// 任何模块不得绕过这些操作直接改写会话。
// 安全约束：刷新令牌只存内存不持久化（FRONTEND.md §7 持久化白名单），刷新页面需重新登录。
export interface SessionSnapshot {
  userId: string
  username: string
  email?: string
  workspaceId: string
  roles: string[]
}

export const useSessionStore = defineStore('session', () => {
  const snapshot = ref<SessionSnapshot | null>(null)
  const accessToken = ref('')
  const refreshToken = ref('')

  const loggedIn = computed(() => snapshot.value !== null)

  /** 一次性接受服务端会话快照（用户、权限、工作空间整体替换，不逐字段修改）。 */
  function accept(next: SessionSnapshot): void {
    snapshot.value = next
  }

  /** 令牌轮换（401 单飞刷新成功后调用，FRONTEND.md §8.1）。 */
  function setTokens(nextAccess: string, nextRefresh: string): void {
    accessToken.value = nextAccess
    refreshToken.value = nextRefresh
  }

  /** 登录成功：整体写入会话快照与令牌。 */
  function establish(next: SessionSnapshot, access: string, refresh: string): void {
    accept(next)
    setTokens(access, refresh)
  }

  /** 清理会话并复位全部领域状态（登出、刷新失败时调用）。 */
  function clear(): void {
    snapshot.value = null
    accessToken.value = ''
    refreshToken.value = ''
  }

  return { snapshot, accessToken, refreshToken, loggedIn, accept, setTokens, establish, clear }
})
