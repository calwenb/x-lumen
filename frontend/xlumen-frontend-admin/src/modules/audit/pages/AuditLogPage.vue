<script setup lang="ts">
// A04 审计日志：时间/操作人/动作/目标/详情表格 + 分页 + action 筛选；detailJson 格式化展示。
// 关键状态：加载骨架、失败重试、空态。
import { onMounted, ref } from 'vue'

import { fetchAuditLogs } from '../api/audit'
import type { AuditLogRecord } from '../api/audit'

const PAGE_SIZE = 20

const records = ref<AuditLogRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)

const actionFilter = ref('')

/** 日期显示：yyyy-MM-dd HH:mm。 */
function formatTime(iso: string): string {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '—'
}

/** detailJson 格式化：尝试解析并美化，失败回退原文。 */
function formatDetail(json: string): string {
  if (!json) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

/** 目标展示：targetType + targetId。 */
function formatTarget(record: AuditLogRecord): string {
  const parts = [record.targetType, record.targetId].filter(Boolean)
  return parts.join(' ') || '—'
}

async function load(targetPage = pageNo.value): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchAuditLogs({
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
      ...(actionFilter.value.trim() ? { action: actionFilter.value.trim() } : {}),
    })
    records.value = page.records
    total.value = page.total
    pageNo.value = targetPage
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function totalPages(): number {
  return Math.max(1, Math.ceil(total.value / PAGE_SIZE))
}

function applyFilter(): void {
  void load(1)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="audit">
    <h1 class="audit__title">审计日志</h1>

    <div class="audit__filters">
      <input
        v-model="actionFilter"
        class="audit__filter-input"
        type="search"
        placeholder="动作筛选，如 LOGIN"
        aria-label="动作筛选"
        @keyup.enter="applyFilter"
      />
      <button type="button" class="audit__filter-button" @click="applyFilter">筛选</button>
    </div>

    <div v-if="loading" class="audit__skeleton" role="status">加载中…</div>
    <div v-else-if="loadError" class="audit__error">
      <p>加载失败，请稍后重试。</p>
      <button type="button" class="audit__retry" @click="load()">重试</button>
    </div>
    <div v-else-if="records.length === 0" class="audit__empty">暂无审计日志</div>
    <template v-else>
      <div class="audit__table-wrap">
        <table class="audit__table">
          <thead>
            <tr>
              <th>时间</th>
              <th>操作人</th>
              <th>动作</th>
              <th>目标</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td class="audit__cell-time">{{ formatTime(record.createdAt) }}</td>
              <td>{{ record.operatorName }}</td>
              <td>{{ record.action }}</td>
              <td>{{ formatTarget(record) }}</td>
              <td>
                <details v-if="formatDetail(record.detailJson)" class="audit__detail">
                  <summary>查看</summary>
                  <pre class="audit__detail-body">{{ formatDetail(record.detailJson) }}</pre>
                </details>
                <span v-else class="audit__detail-empty">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav class="audit__pagination" aria-label="分页">
        <button
          type="button"
          class="audit__page-button"
          :disabled="pageNo <= 1"
          @click="load(pageNo - 1)"
        >
          上一页
        </button>
        <span class="audit__page-info">{{ pageNo }} / {{ totalPages() }}（共 {{ total }} 条）</span>
        <button
          type="button"
          class="audit__page-button"
          :disabled="pageNo >= totalPages()"
          @click="load(pageNo + 1)"
        >
          下一页
        </button>
      </nav>
    </template>
  </main>
</template>

<style scoped>
.audit {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-space-4);
}

.audit__title {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: 22px;
}

.audit__filters {
  display: flex;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-4);
}

.audit__filter-input {
  width: 100%;
  max-width: 280px;
  padding: 7px 10px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: 13px;
}

.audit__filter-input:focus {
  outline: none;
  border-color: var(--xl-color-primary);
}

.audit__filter-button {
  padding: 7px 16px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-color-primary);
  font-size: 13px;
  cursor: pointer;
}

.audit__skeleton,
.audit__error,
.audit__empty {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.audit__retry {
  margin-top: var(--xl-space-3);
  padding: 6px 18px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-color-primary);
  cursor: pointer;
}

.audit__table-wrap {
  overflow-x: auto;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.audit__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.audit__table th,
.audit__table td {
  padding: var(--xl-space-3) var(--xl-space-4);
  text-align: left;
  border-bottom: 1px solid var(--xl-border);
  overflow-wrap: anywhere;
}

.audit__table th {
  color: var(--xl-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.audit__table tbody tr:last-child td {
  border-bottom: none;
}

.audit__cell-time {
  white-space: nowrap;
}

.audit__detail summary {
  color: var(--xl-color-primary);
  font-size: 13px;
  cursor: pointer;
}

.audit__detail-body {
  margin: var(--xl-space-2) 0 0;
  padding: var(--xl-space-3);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-family: var(--xl-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.audit__detail-empty {
  color: var(--xl-text-muted);
}

.audit__pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--xl-space-4);
  margin-top: var(--xl-space-6);
}

.audit__page-button {
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.audit__page-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.audit__page-button:hover:not(:disabled) {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.audit__page-info {
  color: var(--xl-text-muted);
  font-size: 13px;
}
</style>
