// 统一响应结构（FRONTEND.md §8.1，与后端 ApiResponse 对齐）
export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  requestId: string
}
