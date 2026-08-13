import axios, { AxiosError } from 'axios'

import router from '@/router'
import { useSessionStore } from '@/stores/session'
import { createRequestId } from '@/utils/request-id'

import type { ApiResponse } from './types'

// 统一 http 客户端（FRONTEND.md §8.1）：页面不得直接调用 Axios，只调用本封装。
// 401 单飞刷新：并发 401 共享同一刷新 Promise，刷新成功后重放原请求；/auth/ 路径豁免。
// 刷新失败：清理会话并跳转登录页（FRONTEND.md §9 守卫亦会兜底拦截）。
const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

// 已重试过的请求：刷新后不再重试（防重试循环，FRONTEND.md §8.1 WeakSet）
const retried = new WeakSet<object>()
// 单飞刷新 Promise：并发 401 共享
let refreshing: Promise<boolean> | null = null

http.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', createRequestId())
  const session = useSessionStore()
  if (session.accessToken) {
    config.headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const axiosError = error as AxiosError<ApiResponse<unknown>>
    const config = axiosError.config
    const status = axiosError.response?.status
    const url = config?.url ?? ''
    // 仅对需要认证的接口做单飞刷新；/auth/ 路径豁免（FRONTEND.md §8.1）
    if (status === 401 && config && !url.startsWith('/auth/') && !retried.has(config)) {
      retried.add(config)
      try {
        const ok = await refreshAccessToken()
        if (ok) {
          // 刷新成功后重放原请求（携带新令牌）
          return http(config)
        }
      } catch {
        // 刷新失败：清理会话并回到登录页
      }
      useSessionStore().clear()
      redirectToLogin()
    }
    return Promise.reject(error)
  },
)

/** 单飞刷新：共享同一刷新 Promise，避免并发刷新风暴。 */
function refreshAccessToken(): Promise<boolean> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

async function doRefresh(): Promise<boolean> {
  const session = useSessionStore()
  if (!session.refreshToken) {
    return false
  }
  try {
    const { data } = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
      '/api/v1/auth/refresh',
      { refreshToken: session.refreshToken },
      { headers: { 'X-Request-Id': createRequestId() } },
    )
    if (data.code !== 'SUCCESS' || !data.data) {
      return false
    }
    // 刷新成功：轮换令牌（旧刷新令牌已一次性失效）
    session.setTokens(data.data.accessToken, data.data.refreshToken)
    return true
  } catch {
    return false
  }
}

/** 会话失效：跳转登录页并携带回跳地址（当前页已非登录页时）。 */
function redirectToLogin(): void {
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
}

/** 解包统一响应：code 非 SUCCESS 抛出业务错误。 */
export function unwrap<T>(body: ApiResponse<T>): T {
  if (body.code !== 'SUCCESS') {
    throw new Error(body.message || body.code)
  }
  return body.data as T
}

export { http }
