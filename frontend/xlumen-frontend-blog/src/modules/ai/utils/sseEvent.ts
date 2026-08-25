// ai 模块：SSE 事件名常量（与后端 xlumen-ai util/SseEventName.java 对应，C 批去重）。
// 覆盖任务事件（chunk/progress/done/error）与对话事件（tool/citation/done/error），
// chat/api/chat.ts 与 ai/pages/AiWritePage.vue 共用，避免事件名字符串散落。
export const SseEventName = {
  chunk: 'chunk',
  progress: 'progress',
  done: 'done',
  error: 'error',
  citation: 'citation',
  tool: 'tool',
} as const
