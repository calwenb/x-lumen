// chat 模块工具：问答转知识草稿（对话组能力）。
// 复用 content 模块的草稿自动保存接口新建草稿；新建草稿须归属知识库（决策 D16），
// 无归属信息时优先取当前空间的公开库兜底。
import { autosaveDraft } from '@/modules/content/api/knowledge'
import { fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'

import type { Citation } from '@/modules/chat/api/chat'

/** 标题取自用户问题前 30 字；无问题时用通用标题兜底。 */
export function buildDraftTitle(question: string): string {
  return (question.trim() || '对话回答').slice(0, 30)
}

/** 草稿正文：回答文本 + 引用来源清单（有引用时追加，便于创作中心溯源完善）。 */
export function buildDraftContent(answer: string, citations: Citation[]): string {
  const parts = [answer.trim()]
  if (citations.length > 0) {
    parts.push('')
    parts.push('## 参考来源')
    citations.forEach((citation, index) => {
      parts.push(`${index + 1}. ${citation.title || '未命名知识'}`)
    })
  }
  return parts.join('\n')
}

/** 以回答内容新建知识草稿（草稿态，创作中心可继续完善）。 */
export async function saveChatDraft(title: string, content: string): Promise<void> {
  // 新建草稿须归属知识库（决策 D16）：优先公开库，否则取第一个库兜底
  const bases = await fetchKnowledgeBases().catch(() => [])
  const kbId = (bases.find((kb) => kb.visibility === 1) ?? bases[0])?.id
  if (!kbId) {
    throw new Error('未找到可归属的知识库，请先创建知识库')
  }
  await autosaveDraft({ title, content, kbId })
}
