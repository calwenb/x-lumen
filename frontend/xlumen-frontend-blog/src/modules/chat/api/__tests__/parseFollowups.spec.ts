// chat API：parseFollowups（后端 followups 事件负载为 JSON 字符串数组）。
import { describe, expect, it } from 'vitest'

import { parseFollowups } from '@/modules/chat/api/chat'

describe('parseFollowups', () => {
  it('解析字符串数组为追问建议', () => {
    expect(parseFollowups('["追问1","追问2","追问3"]')).toEqual(['追问1', '追问2', '追问3'])
  })

  it('过滤非字符串与空白项', () => {
    expect(parseFollowups('["追问1",123,null,"  "]')).toEqual(['追问1'])
  })

  it('空数组 / 非法 JSON 返回空数组', () => {
    expect(parseFollowups('[]')).toEqual([])
    expect(parseFollowups('not-json')).toEqual([])
  })
})
