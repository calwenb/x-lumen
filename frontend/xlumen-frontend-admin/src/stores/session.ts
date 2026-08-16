import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

// 全局会话 Store（FRONTEND.md §7）：仅提供 accept()/clear()/setTokens()/establish() 原子操作，
// 任何模块不得绕过这些操作直接改写会话。
// 持久化策略（FRONTEND.md §7 安全约束）：会话快照 + accessToken 写入 localStorage 以支撑整页刷新，
// Refresh Token 只存内存不持久化——刷新页面不再立即登出，accessToken 过期后仍要求重新登录。
export interface SessionSnapshot {
  userId: string
  username: string
  email?: string
  workspaceId: string
  roles: string[]
}

const STORAGE_KEY = 'xlumen.admin.session'

interface PersistedSession {
  snapshot: SessionSnapshot
  accessToken: string
}

/** 从 localStorage 恢复会话（损坏/缺失返回 null，隐私模式等写失败静默）。 */
function loadSaved(): PersistedSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as PersistedSession
    if (!parsed?.snapshot?.userId || !parsed.accessToken) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export const useSessionStore = defineStore('session', () => {
  const saved = loadSaved()
  const snapshot = ref<SessionSnapshot | null>(saved?.snapshot ?? null)
  const accessToken = ref(saved?.accessToken ?? '')
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
    persist()
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
    persist()
  }

  /** 持久化非敏感会话（不写 Refresh Token，FRONTEND.md §7 白名单约束）。 */
  function persist(): void {
    try {
      if (snapshot.value && accessToken.value) {
        localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({
            snapshot: snapshot.value,
            accessToken: accessToken.value,
          } satisfies PersistedSession),
        )
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // 隐私模式/禁用存储等场景：会话仅存内存，行为退化为刷新即登出
    }
  }

  return { snapshot, accessToken, refreshToken, loggedIn, accept, setTokens, establish, clear }
})
