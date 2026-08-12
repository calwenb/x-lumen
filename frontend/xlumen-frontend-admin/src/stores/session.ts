import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

// 全局会话 Store（FRONTEND.md §7）：仅提供 accept()/clear() 两个原子操作，
// 任何模块不得绕过这两个操作直接改写会话；不持久化敏感信息。
export interface SessionSnapshot {
  userId: number
  username: string
  workspaceId: number
  roles: string[]
}

export const useSessionStore = defineStore('session', () => {
  const snapshot = ref<SessionSnapshot | null>(null)

  const loggedIn = computed(() => snapshot.value !== null)

  /** 一次性接受服务端会话快照（用户、权限、工作空间整体替换，不逐字段修改）。 */
  function accept(next: SessionSnapshot): void {
    snapshot.value = next
  }

  /** 清理会话并复位全部领域状态（登出、刷新失败时调用）。 */
  function clear(): void {
    snapshot.value = null
  }

  return { snapshot, loggedIn, accept, clear }
})
