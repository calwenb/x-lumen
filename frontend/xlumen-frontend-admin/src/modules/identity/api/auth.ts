import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

// 身份接口封装（F-0101）：管理后台仅登录/登出，页面只调用本文件暴露的函数（FRONTEND.md §5/§8.1）。

export interface TokenPayload {
  accessToken: string
  refreshToken: string
  expiresIn: number
  workspaceId: string
  user: {
    userId: string
    username: string
    email?: string
    roles: string[]
  }
}

/** 登录：失败由后端统一提示，前端不暴露账号是否存在（防枚举）。 */
export async function loginApi(username: string, password: string): Promise<TokenPayload> {
  const { data } = await http.post<ApiResponse<TokenPayload>>('/auth/login', { username, password })
  return unwrap(data)
}

/** 登出：撤销刷新令牌。 */
export async function logoutApi(refreshToken: string): Promise<void> {
  await http.post<ApiResponse<unknown>>('/auth/logout', { refreshToken })
}
