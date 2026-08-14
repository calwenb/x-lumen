<script setup lang="ts">
// 发布管理（B13，F-0905/F-0906，决策 D16）：已通过知识列表（调 content fetchKnowledges status=4）→
// 展示知识当前归属（库/目录，发布目标由知识归属决定）→ 立即/定时发布（二次确认）+ 下方发布记录列表。
// 文章级可见性已废弃（KB-3 起 CreateReleaseDTO 删除 visibility，可见性由知识库决定）。
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { fetchKnowledges, VISIBILITY_LABELS } from '@/modules/content/api/knowledge'
import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { createRelease, fetchReleases } from '@/modules/publishing/api/release'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { KnowledgeListItem } from '@/modules/content/api/knowledge'
import type { DirectoryNode } from '@/modules/knowledge/api/knowledgeBase'
import type { ReleaseVO } from '@/modules/publishing/api/release'

const PAGE_SIZE = 10
const APPROVED_STATUS = 4

/** 已通过知识行（含本地发布控制态 + 归属展示信息）。 */
interface ReleaseRow {
  knowledge: KnowledgeListItem
  /** 归属知识库名（kbId 为空则为空串）。 */
  kbName: string
  /** 归属目录名（库根=「库根」，kbId 为空则为空串）。 */
  directoryName: string
  publishAt: string
  releasing: boolean
}

const RELEASE_STATUS_LABELS: Record<string, string> = {
  PENDING: '待发布',
  DONE: '已发布',
  FAILED: '发布失败',
}

const approved = ref<ReleaseRow[]>([])
const approvedLoading = ref(true)
const approvedError = ref(false)

const releases = ref<ReleaseVO[]>([])
const releasesTotal = ref(0)
const releasesPageNo = ref(1)
const releasesLoading = ref(true)
const releasesError = ref(false)

// 归属名称索引（名称解析失败降级为占位文案，不阻断列表）
const kbNameById = new Map<string, string>()
/** `${kbId}:${directoryId}` → 目录名。 */
const dirNameByKbDir = new Map<string, string>()
const loadedKbs = new Set<string>()

function formatTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

/** datetime-local 值补足秒，满足 ISO LocalDateTime。 */
function normalizePublishAt(value: string): string {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? `${value}:00` : value
}

function flattenDirectories(nodes: DirectoryNode[], out: DirectoryNode[] = []): DirectoryNode[] {
  for (const node of nodes) {
    out.push(node)
    flattenDirectories(node.children, out)
  }
  return out
}

async function loadKbIndex(): Promise<void> {
  try {
    const kbs = await fetchKnowledgeBases()
    for (const kb of kbs) kbNameById.set(kb.id, kb.name)
  } catch {
    // 库名解析失败：归属展示降级为「未知知识库」
  }
}

async function loadDirectoryIndex(kbId: string): Promise<void> {
  if (loadedKbs.has(kbId)) return
  loadedKbs.add(kbId)
  try {
    const nodes = await fetchDirectoryTree(kbId)
    for (const node of flattenDirectories(nodes)) {
      dirNameByKbDir.set(`${kbId}:${node.id}`, node.name)
    }
  } catch {
    // 目录名解析失败：归属展示降级为「未知目录」
  }
}

/** 知识归属 → 展示文案（无归属返回空串，由模板提示去编辑页选择）。 */
function resolveTarget(knowledge: KnowledgeListItem): { kbName: string; directoryName: string } {
  if (!knowledge.kbId) return { kbName: '', directoryName: '' }
  const directoryId = knowledge.directoryId
  const directoryName =
    directoryId && directoryId !== '0'
      ? (dirNameByKbDir.get(`${knowledge.kbId}:${directoryId}`) ?? '未知目录')
      : '库根'
  return { kbName: kbNameById.get(knowledge.kbId) ?? '未知知识库', directoryName }
}

async function loadApproved(): Promise<void> {
  approvedLoading.value = true
  approvedError.value = false
  try {
    const page = await fetchKnowledges({ status: APPROVED_STATUS, pageNo: 1, pageSize: 50 })
    await loadKbIndex()
    const kbIds = [
      ...new Set(page.records.map((item) => item.kbId).filter((id): id is string => Boolean(id))),
    ]
    await Promise.all(kbIds.map(loadDirectoryIndex))
    approved.value = page.records.map((knowledge) => {
      const target = resolveTarget(knowledge)
      return {
        knowledge,
        kbName: target.kbName,
        directoryName: target.directoryName,
        publishAt: '',
        releasing: false,
      }
    })
  } catch {
    approvedError.value = true
  } finally {
    approvedLoading.value = false
  }
}

async function loadReleases(targetPage: number): Promise<void> {
  releasesLoading.value = true
  releasesError.value = false
  try {
    const page = await fetchReleases({ pageNo: targetPage, pageSize: PAGE_SIZE })
    releases.value = page.records
    releasesTotal.value = page.total
    releasesPageNo.value = targetPage
  } catch {
    releasesError.value = true
  } finally {
    releasesLoading.value = false
  }
}

async function releaseNow(row: ReleaseRow): Promise<void> {
  if (row.releasing) return
  try {
    await ElMessageBox.confirm(`确认立即发布「${row.knowledge.title}」吗？`, '立即发布', {
      confirmButtonText: '立即发布',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消
    return
  }
  await doRelease(row, undefined)
}

async function releaseScheduled(row: ReleaseRow): Promise<void> {
  if (row.releasing) return
  if (!row.publishAt) {
    ElMessage.warning('请先选择定时发布时间')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认于 ${row.publishAt.replace('T', ' ')} 定时发布「${row.knowledge.title}」吗？`,
      '定时发布',
      {
        confirmButtonText: '定时发布',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    // 用户取消
    return
  }
  await doRelease(row, normalizePublishAt(row.publishAt))
}

async function doRelease(row: ReleaseRow, publishAt?: string): Promise<void> {
  row.releasing = true
  try {
    // 发布目标（库/目录）由知识本身归属决定（KB-3 起不再传 visibility，决策 D16）
    await createRelease({
      knowledgeId: row.knowledge.id,
      version: row.knowledge.version,
      ...(publishAt ? { publishAt } : {}),
    })
    ElMessage.success('发布成功，已建立 RAG 索引')
    await Promise.all([loadApproved(), loadReleases(1)])
  } catch (error) {
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '发布失败，请稍后重试',
    )
  } finally {
    row.releasing = false
  }
}

onMounted(() => {
  void loadApproved()
  void loadReleases(1)
})
</script>

<template>
  <main class="release-page">
    <header class="release-page__header">
      <h1 class="release-page__title">发布管理</h1>
      <p class="release-page__intro">
        对已通过审核的知识执行立即/定时发布，发布成功自动建立 RAG
        索引；发布目标取自知识所属知识库/目录。
      </p>
    </header>

    <section class="release-page__section">
      <h2 class="release-page__section-title">待发布知识（已通过审核）</h2>
      <div v-if="approvedLoading" class="release-page__state">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="approvedError" class="release-page__state">
        <p>待发布知识加载失败</p>
        <el-button type="primary" plain size="small" @click="loadApproved">重试</el-button>
      </div>
      <div v-else-if="approved.length === 0" class="release-page__state">暂无待发布知识。</div>
      <ul v-else class="release-page__list">
        <li v-for="row in approved" :key="row.knowledge.id" class="release-row">
          <div class="release-row__main">
            <span class="release-row__title">{{ row.knowledge.title }}</span>
            <span v-if="row.knowledge.kbId" class="release-row__meta">
              v{{ row.knowledge.version }} · 目标：知识库{{ row.kbName }} · 目录{{
                row.directoryName
              }}
            </span>
            <span v-else class="release-row__meta release-row__meta--warn">
              未归属知识库，请先在编辑页选择知识库
            </span>
          </div>
          <div class="release-row__actions">
            <input
              v-model="row.publishAt"
              class="release-row__datetime"
              type="datetime-local"
              aria-label="定时发布时间"
            />
            <el-button
              type="primary"
              size="small"
              :loading="row.releasing"
              :disabled="!row.knowledge.kbId"
              @click="releaseNow(row)"
            >
              立即发布
            </el-button>
            <el-button
              type="primary"
              plain
              size="small"
              :loading="row.releasing"
              :disabled="!row.knowledge.kbId"
              @click="releaseScheduled(row)"
            >
              定时发布
            </el-button>
          </div>
        </li>
      </ul>
    </section>

    <section class="release-page__section">
      <h2 class="release-page__section-title">发布记录</h2>
      <div v-if="releasesLoading" class="release-page__state">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="releasesError" class="release-page__state">
        <p>发布记录加载失败</p>
        <el-button type="primary" plain size="small" @click="loadReleases(releasesPageNo)"
          >重试</el-button
        >
      </div>
      <div v-else-if="releases.length === 0" class="release-page__state">暂无发布记录。</div>
      <template v-else>
        <ul class="release-page__records">
          <li v-for="record in releases" :key="record.id" class="release-record">
            <span class="release-record__title">{{ record.knowledgeTitle }}</span>
            <span class="release-record__meta">
              {{ VISIBILITY_LABELS[record.visibility] ?? record.visibility }} ·
              {{ RELEASE_STATUS_LABELS[record.status] ?? record.status }}
            </span>
            <span class="release-record__time">
              {{
                record.releasedAt
                  ? formatTime(record.releasedAt)
                  : record.publishAt
                    ? `计划 ${formatTime(record.publishAt)}`
                    : '—'
              }}
            </span>
          </li>
        </ul>
        <Pagination
          :page-no="releasesPageNo"
          :page-size="PAGE_SIZE"
          :total="releasesTotal"
          @change="loadReleases"
        />
      </template>
    </section>
  </main>
</template>

<style scoped>
.release-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.release-page__header {
  margin-bottom: 24px;
}

.release-page__title {
  margin: 0;
  font-size: 24px;
}

.release-page__intro {
  margin: 8px 0 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.release-page__section {
  margin-bottom: 32px;
}

.release-page__section-title {
  margin: 0 0 12px;
  font-size: 17px;
}

.release-page__state {
  padding: 32px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.release-page__state :deep(.el-skeleton) {
  text-align: left;
}

.release-page__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.release-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.release-row__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.release-row__title {
  font-size: 15px;
  font-weight: 600;
  overflow-wrap: break-word;
}

.release-row__meta {
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.release-row__meta--warn {
  color: var(--xl-color-warning);
}

.release-row__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.release-row__datetime {
  padding: 6px 10px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: 13px;
  outline: none;
}

.release-row__datetime:focus {
  border-color: var(--xl-color-primary);
}

.release-page__records {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.release-record {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.release-record__title {
  flex: 1;
  min-width: 200px;
  font-size: 14px;
  overflow-wrap: break-word;
}

.release-record__meta {
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.release-record__time {
  color: var(--xl-text-muted);
  font-size: 12px;
}
</style>
