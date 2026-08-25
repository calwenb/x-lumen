// chat 模块：工具过程事件面板工具（IDEA-025 工具轨迹统一渲染口径，D 批去重）。
// ChatPage 与 KnowledgeQaDialog 共用，避免两处完全相同的过滤/排序逻辑。
import type { ToolEvent } from '@/modules/chat/api/chat'

/** 进行中的工具事件（start 未配对 done）。 */
export function activeTools(tools: ToolEvent[]): ToolEvent[] {
  const doneSeqs = new Set(tools.filter((t) => t.phase === 'done').map((t) => t.seq))
  return tools.filter((t) => t.phase === 'start' && !doneSeqs.has(t.seq))
}

/** 已完成的工具事件（seq 升序）。 */
export function doneTools(tools: ToolEvent[]): ToolEvent[] {
  return tools.filter((t) => t.phase === 'done').sort((a, b) => a.seq - b.seq)
}
