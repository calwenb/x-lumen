<script setup lang="ts">
// 发布管理（B13，F-0905/F-0906）：已通过知识列表（调 content fetchKnowledges status=4）→
// 每篇选可见性 + 立即/定时发布（二次确认）+ 下方发布记录列表。
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { fetchKnowledges, VISIBILITY_LABELS } from '@/modules/content/api/knowledge'
import { createRelease, fetchReleases } from '@/modules/publishing/api/release'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { KnowledgeListItem } from '@/modules/content/api/knowledge'
import type { ReleaseVO } from '@/modules/publishing/api/release'

const PAGE_SIZE = 10
const APPROVED_STATUS = 4

/** 已通过知识行（含本地发布控制态）。 */
interface ReleaseRow {
  knowledge: KnowledgeListItem
  visibility: number
  publishAt: string
  releasing: boolean
}

const RELEASE_STATUS_LABELS: Record<string, string> = {
  PENDING: '待发布',
  SCHEDULED: '定时发布',
  PUBLISHED: '已发布',
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

function formatTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

/** datetime-local 值补足秒，满足 ISO LocalDateTime。 */
function normalizePublishAt(value: string): string {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? `${value}:00` : value
}

async function loadApproved(): Promise<void> {
  approvedLoading.value = true
  approvedError.value = false
  try {
    const page = await fetchKnowledges({ status: APPROVED_STATUS, pageNo: 1, pageSize: 50 })
    approved.value = page.records.map((knowledge) => ({
      knowledge,
      visibility: knowledge.visibility,
      publishAt: '',
      releasing: false,
    }))
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
    await createRelease({
      knowledgeId: row.knowledge.id,
      version: row.knowledge.version,
      visibility: row.visibility,
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
        对已通过审核的知识执行立即/定时发布，发布成功自动建立 RAG 索引。
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
            <span class="release-row__meta">
              v{{ row.knowledge.version }} · {{ row.knowledge.category || '未分类' }}
            </span>
          </div>
          <div class="release-row__actions">
            <el-select v-model="row.visibility" class="release-row__select" aria-label="可见性">
              <el-option :value="1" label="公开" />
              <el-option :value="0" label="私有" />
            </el-select>
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
              @click="releaseNow(row)"
            >
              立即发布
            </el-button>
            <el-button
              type="primary"
              plain
              size="small"
              :loading="row.releasing"
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

.release-row__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.release-row__select {
  width: 110px;
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
