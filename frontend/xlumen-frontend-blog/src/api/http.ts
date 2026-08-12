import axios from 'axios'

import { createRequestId } from '@/utils/request-id'

import type { ApiResponse } from './types'

// 统一 http 客户端（FRONTEND.md §8.1）：页面不得直接调用 Axios，只调用本封装。
// 骨架阶段仅建立请求头与统一响应拦截；401 单飞刷新在 M02 身份模块落地。
export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  config.headers.set('X-Request-Id', createRequestId())
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    // 错误提示展示响应 requestId 供审计追踪（FRONTEND.md §8.1）
    return Promise.reject(error)
  },
)

/** 解包统一响应：code 非 SUCCESS 抛出业务错误。 */
export function unwrap<T>(body: ApiResponse<T>): T {
  if (body.code !== 'SUCCESS') {
    throw new Error(body.message || body.code)
  }
  return body.data as T
}
