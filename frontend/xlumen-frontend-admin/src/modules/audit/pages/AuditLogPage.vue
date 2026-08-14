<script setup lang="ts">
// A04 审计日志：时间/操作人/动作/目标/详情表格 + 分页 + action 筛选；detailJson 弹窗格式化展示。
// 关键状态：加载骨架、失败重试、空态。
import { onMounted, ref } from 'vue'
import { Document } from '@element-plus/icons-vue'

import { fetchAuditLogs } from '../api/audit'
import type { AuditLogRecord } from '../api/audit'

const PAGE_SIZE = 20

const records = ref<AuditLogRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)

const actionFilter = ref('')

// 详情弹窗：当前查看的日志记录
const detailVisible = ref(false)
const detailRecord = ref<AuditLogRecord | null>(null)

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

function openDetail(record: AuditLogRecord): void {
  detailRecord.value = record
  detailVisible.value = true
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
      <el-input
        v-model="actionFilter"
        class="audit__filter-input"
        placeholder="动作筛选，如 LOGIN"
        aria-label="动作筛选"
        clearable
        @keyup.enter="applyFilter"
      />
      <el-button type="primary" plain @click="applyFilter">筛选</el-button>
    </div>

    <div v-if="loading" class="audit__state" role="status">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="loadError" class="audit__state">
      <p>加载失败，请稍后重试。</p>
      <el-button type="primary" plain @click="load()">重试</el-button>
    </div>
    <div v-else-if="records.length === 0" class="audit__state">
      <el-icon class="audit__state-icon"><Document /></el-icon>
      <p>暂无审计日志</p>
    </div>
    <template v-else>
      <el-table
        :data="records"
        class="audit__table"
        :header-cell-style="{ background: 'var(--xl-bg-secondary)' }"
      >
        <el-table-column label="时间" min-width="130">
          <template #default="{ row }">
            <span class="audit__cell-time">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" min-width="110" />
        <el-table-column prop="action" label="动作" min-width="170" />
        <el-table-column label="目标" min-width="180">
          <template #default="{ row }">{{ formatTarget(row) }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90">
          <template #default="{ row }">
            <el-button
              v-if="formatDetail(row.detailJson)"
              type="primary"
              link
              @click="openDetail(row)"
              >查看</el-button
            >
            <span v-else class="audit__detail-empty">—</span>
          </template>
        </el-table-column>
      </el-table>

      <nav class="audit__pagination" aria-label="分页">
        <el-pagination
          :current-page="pageNo"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="(page: number) => load(page)"
        />
      </nav>
    </template>

    <el-dialog v-model="detailVisible" title="审计详情" width="560px">
      <div v-if="detailRecord" class="audit__detail-meta">
        <span>{{ formatTime(detailRecord.createdAt) }}</span>
        <span>{{ detailRecord.operatorName || '系统' }}</span>
        <span>{{ detailRecord.action }}</span>
      </div>
      <pre class="audit__detail-body">{{
        detailRecord ? formatDetail(detailRecord.detailJson) : ''
      }}</pre>
    </el-dialog>
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
}

.audit__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.audit__state-icon {
  display: block;
  margin-bottom: var(--xl-space-3);
  font-size: 40px;
  color: var(--xl-text-muted);
}

.audit__state p {
  margin: 0;
}

.audit__state :deep(.el-skeleton) {
  text-align: left;
}

.audit__table {
  width: 100%;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow: hidden;
}

.audit__table :deep(th.el-table__cell) {
  color: var(--xl-text-secondary);
  font-weight: 600;
}

.audit__cell-time {
  white-space: nowrap;
}

.audit__detail-empty {
  color: var(--xl-text-muted);
}

.audit__pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--xl-space-6);
}

.audit__detail-meta {
  display: flex;
  gap: var(--xl-space-4);
  margin-bottom: var(--xl-space-3);
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.audit__detail-body {
  margin: 0;
  padding: var(--xl-space-3);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-family: var(--xl-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
