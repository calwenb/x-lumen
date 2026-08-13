// ai 模块 API：AI 写作任务（F-0601/F-0604，B11）——提交任务、查询任务、重试。
// 流式输出走 utils/sse.ts（fetch 读取，需 Authorization 头），不在本文件处理。
// ID 为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 写作输入：按输入模式仅携带对应字段（主题/草稿/完整文章）。 */
export interface WritingRequest {
  topic?: string
  draft?: string
  content?: string
  title?: string
}

/** 提交结果：返回任务 ID，随后通过流式接口读取生成进度。 */
export interface WritingTaskSubmitted {
  taskId: string
}

/** AI 写作任务（归一化后）。 */
export interface AiWritingTask {
  id: string
  scene: string
  status: string
  resultJson: string
  errorMsg: string
}

/** 后端原始任务形态。 */
interface RawTask {
  id: string
  scene: string
  status: string
  resultJson: string | null
  errorMsg: string | null
}

/** 提交写作任务（F-0601）。 */
export async function submitWriting(payload: WritingRequest): Promise<WritingTaskSubmitted> {
  const { data } = await http.post<ApiResponse<WritingTaskSubmitted>>('/ai/writing', payload)
  const result = unwrap(data)
  return { taskId: String(result.taskId) }
}

/** 查询写作任务详情（F-0604，流结束后回拉最终结果）。 */
export async function fetchWritingTask(taskId: string): Promise<AiWritingTask> {
  const { data } = await http.get<ApiResponse<RawTask>>(`/ai/tasks/${taskId}`)
  const task = unwrap(data)
  return {
    id: String(task.id),
    scene: task.scene,
    status: task.status,
    resultJson: task.resultJson ?? '',
    errorMsg: task.errorMsg ?? '',
  }
}

/** 重试失败任务（F-0604）。 */
export async function retryWritingTask(taskId: string): Promise<void> {
  await http.post<ApiResponse<unknown>>(`/ai/tasks/${taskId}/retry`)
}
