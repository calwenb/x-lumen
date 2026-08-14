// knowledge 模块 API：知识库/目录/回收站（F-0308/F-0309/F-0305，KB-3 后端能力，决策 D16）。
// ID 为 string（雪花 ID 后端 Long 序列化为 String，BACKEND.md §5.3）；统计数值 Number() 还原。
import { http, unwrap } from '@/api/http'

import type { ApiResponse } from '@/api/types'

/** 知识库（F-0308）。 */
export interface KnowledgeBase {
  id: string
  workspaceId: string
  name: string
  intro: string
  cover: string
  visibility: 0 | 1
  knowledgeCount: number
  createdAt: string
  updatedAt: string
}

/** 目录树节点（F-0309，多级 parent_id）。 */
export interface DirectoryNode {
  id: string
  kbId: string
  parentId: string
  name: string
  knowledgeCount: number
  children: DirectoryNode[]
}

/** 回收站条目（F-0305）。 */
export interface RecycleBinItem {
  type: 'kb' | 'knowledge'
  id: string
  name: string
  kbName: string | null
  deletedAt: string
  directoryName: string | null
}

/** 服务端分页结果。 */
export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}

/** 还原后端 Long→String 的统计数值。 */
function toNumber(value: unknown): number {
  return Number(value ?? 0)
}

interface RawPage<T> {
  total: string
  pageNo: string
  pageSize: string
  records: T[]
}

interface RawKnowledgeBase {
  id: string
  workspaceId: string
  name: string
  intro: string
  cover: string
  visibility: 0 | 1
  knowledgeCount: string
  createdAt: string
  updatedAt: string
}

interface RawDirectoryNode {
  id: string
  kbId: string
  parentId: string
  name: string
  knowledgeCount: string
  children: RawDirectoryNode[]
}

interface RawRecycleBinItem {
  type: 'kb' | 'knowledge'
  id: string
  name: string
  kbName: string | null
  deletedAt: string
  directoryName: string | null
}

/** 我的知识库列表（F-0308，B22 库管理/导航切换器）。 */
export async function fetchKnowledgeBases(): Promise<KnowledgeBase[]> {
  const { data } = await http.get<ApiResponse<RawKnowledgeBase[]>>('/knowledge-bases')
  return unwrap(data).map((kb) => ({ ...kb, knowledgeCount: toNumber(kb.knowledgeCount) }))
}

/** 创建知识库（F-0308）。 */
export async function createKnowledgeBase(payload: {
  name: string
  intro?: string
  cover?: string
  visibility?: 0 | 1
}): Promise<KnowledgeBase> {
  const { data } = await http.post<ApiResponse<RawKnowledgeBase>>('/knowledge-bases', payload)
  const kb = unwrap(data)
  return { ...kb, knowledgeCount: toNumber(kb.knowledgeCount) }
}

/** 更新知识库（F-0308）。 */
export async function updateKnowledgeBase(kbId: string, payload: { name?: string; intro?: string; cover?: string }): Promise<KnowledgeBase> {
  const { data } = await http.put<ApiResponse<RawKnowledgeBase>>(`/knowledge-bases/${kbId}`, payload)
  const kb = unwrap(data)
  return { ...kb, knowledgeCount: toNumber(kb.knowledgeCount) }
}

/** 删除知识库（F-0308，二次确认 confirm=CONFIRM，连带回收站）。 */
export async function deleteKnowledgeBase(kbId: string, confirm = 'CONFIRM'): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/knowledge-bases/${kbId}`, { params: { confirm } })
  unwrap(data)
}

/** 切换知识库可见性（F-0308，0 私有/1 公开，即时生效）。 */
export async function changeKnowledgeBaseVisibility(kbId: string, visibility: 0 | 1): Promise<KnowledgeBase> {
  const { data } = await http.put<ApiResponse<RawKnowledgeBase>>(`/knowledge-bases/${kbId}/visibility`, { visibility })
  const kb = unwrap(data)
  return { ...kb, knowledgeCount: toNumber(kb.knowledgeCount) }
}

/** 目录树（F-0309，按名称排序）。 */
export async function fetchDirectoryTree(kbId: string): Promise<DirectoryNode[]> {
  const { data } = await http.get<ApiResponse<RawDirectoryNode[]>>(`/knowledge-bases/${kbId}/directories`)
  return unwrap(data).map((node) => mapDirectory(node))
}

function mapDirectory(node: RawDirectoryNode): DirectoryNode {
  return {
    id: node.id,
    kbId: node.kbId,
    parentId: node.parentId,
    name: node.name,
    knowledgeCount: toNumber(node.knowledgeCount),
    children: (node.children ?? []).map(mapDirectory),
  }
}

/** 创建目录（F-0309）。 */
export async function createDirectory(kbId: string, payload: { parentId?: string; name: string }): Promise<DirectoryNode> {
  const { data } = await http.post<ApiResponse<RawDirectoryNode>>(`/knowledge-bases/${kbId}/directories`, payload)
  return mapDirectory(unwrap(data))
}

/** 更新目录（F-0309）。 */
export async function updateDirectory(kbId: string, directoryId: string, payload: { name: string }): Promise<DirectoryNode> {
  const { data } = await http.put<ApiResponse<RawDirectoryNode>>(
    `/knowledge-bases/${kbId}/directories/${directoryId}`,
    payload,
  )
  return mapDirectory(unwrap(data))
}

/** 删除目录（F-0309，目录下知识上挂父目录）。 */
export async function deleteDirectory(kbId: string, directoryId: string): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/knowledge-bases/${kbId}/directories/${directoryId}`)
  unwrap(data)
}

/** 回收站分页（F-0305，type=kb|knowledge|空=全部）。 */
export async function fetchRecycleBin(
  params: { type?: 'kb' | 'knowledge'; pageNo?: number; pageSize?: number } = {},
): Promise<PageResult<RecycleBinItem>> {
  const { data } = await http.get<ApiResponse<RawPage<RawRecycleBinItem>>>('/recycle-bin', { params })
  const body = unwrap(data)
  return {
    total: toNumber(body.total),
    pageNo: toNumber(body.pageNo),
    pageSize: toNumber(body.pageSize),
    records: body.records.map((item) => ({ ...item })),
  }
}

/** 恢复回收站条目（F-0305）。 */
export async function restoreRecycleBinItem(type: 'kb' | 'knowledge', id: string): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>(`/recycle-bin/${type}/${id}/restore`)
  unwrap(data)
}

/** 彻底删除回收站条目（F-0305，二次确认 confirm=CONFIRM）。 */
export async function purgeRecycleBinItem(type: 'kb' | 'knowledge', id: string, confirm = 'CONFIRM'): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/recycle-bin/${type}/${id}`, { params: { confirm } })
  unwrap(data)
}
