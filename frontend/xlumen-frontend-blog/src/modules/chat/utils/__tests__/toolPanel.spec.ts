// toolPanel 工具：activeTools（start 未配对 done）/ doneTools（done 按 seq 升序）。
import { describe, expect, it } from 'vitest'

import { activeTools, doneTools } from '@/modules/chat/utils/toolPanel'

import type { ToolEvent } from '@/modules/chat/api/chat'

function tool(seq: number, phase: ToolEvent['phase'], name = 'knowledge.search'): ToolEvent {
  return { seq, name, phase }
}

describe('toolPanel', () => {
  it('activeTools 只返回未配对的 start', () => {
    const events = [tool(1, 'start'), tool(2, 'start'), tool(2, 'done'), tool(3, 'done')]
    expect(activeTools(events).map((t) => t.seq)).toEqual([1])
  })

  it('doneTools 按 seq 升序返回已完成', () => {
    const events = [tool(3, 'done'), tool(1, 'done'), tool(2, 'start'), tool(2, 'done')]
    expect(doneTools(events).map((t) => t.seq)).toEqual([1, 2, 3])
  })
})
