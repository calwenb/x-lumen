// knowledge 模块 API：RAG 检索测试 + 知识索引状态（F-0402~F-0405，M05，对应后端 xlumen-knowledge kb_）。
// ID 为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）；chunkSeq/score/chunkCount Number() 还原。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 检索命中项。 */
export interface RetrievalItem {
  knowledgeId: string
  title: string
  chunkSeq: number
  headingAnchor: string
  chunkText: string
  score: number
  visibility: number
}

/** 知识索引状态。 */
export interface IndexStatus {
  knowledgeId: string
  version: string
  status: string
  chunkCount: number
  indexedAt: string | null
}

interface RawRetrievalItem {
  knowledgeId: string
  title: string
  chunkSeq: string
  headingAnchor: string
  chunkText: string
  score: string
  visibility: string
}

interface RawIndexStatus {
  knowledgeId: string
  version: string
  status: string
  chunkCount: string
  indexedAt: string | null
}

/** RAG 检索测试（F-0405）。 */
export async function retrievalTest(query: string, topK: number): Promise<RetrievalItem[]> {
  const { data } = await http.post<ApiResponse<RawRetrievalItem[]>>('/knowledge/retrieval-test', { query, topK })
  return unwrap(data).map((item) => ({
    knowledgeId: String(item.knowledgeId),
    title: item.title ?? '',
    chunkSeq: Number(item.chunkSeq ?? 0),
    headingAnchor: item.headingAnchor ?? '',
    chunkText: item.chunkText ?? '',
    score: Number(item.score ?? 0),
    visibility: Number(item.visibility ?? 0),
  }))
}

/** 知识索引状态查询（F-0402，未建立索引返回 null）。 */
export async function fetchIndexStatus(knowledgeId: string): Promise<IndexStatus | null> {
  const { data } = await http.get<ApiResponse<RawIndexStatus | null>>(`/knowledge/${knowledgeId}/index-status`)
  const status = unwrap(data)
  if (!status) return null
  return {
    knowledgeId: String(status.knowledgeId),
    version: String(status.version ?? ''),
    status: status.status ?? '',
    chunkCount: Number(status.chunkCount ?? 0),
    indexedAt: status.indexedAt ?? null,
  }
}
