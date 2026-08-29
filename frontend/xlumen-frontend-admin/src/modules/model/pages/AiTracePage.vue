<script setup lang="ts">
// AI 调用追踪：今日统计卡（调用/失败/成功）+ 场景与结果筛选 + 调用明细表格 + 分页。
// 关键状态：汇总/列表加载、失败重试、空态。
import { computed, onMounted, ref } from 'vue'
import { Monitor } from '@element-plus/icons-vue'

import { SCENE_LABELS } from '../api/model'
import { fetchAiTraceSummary, fetchAiTraces } from '../api/trace'

import type { AiTracePage, AiTraceQuery, AiTraceSummary } from '../api/trace'

const PAGE_SIZE = 20

const records = ref<AiTracePage['records']>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)

const summary = ref<AiTraceSummary | null>(null)

const sceneFilter = ref('')
// 结果筛选：空串表示全部，'true'/'false' 对应成功/失败。
const resultFilter = ref<'' | 'true' | 'false'>('')

/** 今日成功 = 今日调用 - 今日失败。 */
const todaySuccess = computed(() => {
  const s = summary.value
  if (!s) {
    return 0
  }
  return Math.max(s.todayCount - s.todayFailed, 0)
})

function formatTime(iso: string): string {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '—'
}

/** 费用展示：保留 4 位小数。 */
function formatCost(cost: number): string {
  return Number.isFinite(cost) ? cost.toFixed(4) : '—'
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await fetchAiTraceSummary()
  } catch {
    summary.value = null
  }
}

async function load(targetPage = pageNo.value): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const query: AiTraceQuery = {
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
      ...(sceneFilter.value ? { scene: sceneFilter.value } : {}),
      ...(resultFilter.value !== '' ? { success: resultFilter.value === 'true' } : {}),
    }
    const page = await fetchAiTraces(query)
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

function retry(): void {
  void load(1)
  void loadSummary()
}

onMounted(() => {
  void load()
  void loadSummary()
})
</script>

<template>
  <main class="trace">
    <h1 class="trace__title">AI 调用追踪</h1>

    <!-- A03 顶部：连续统计带 + 同排右侧筛选 -->
    <div class="trace__top">
      <section class="trace__stats" aria-label="今日统计">
        <div class="trace__stat">
          <span class="trace__stat-label">今日调用</span>
          <span class="trace__stat-value">{{ summary ? summary.todayCount : '—' }}</span>
        </div>
        <div class="trace__stat trace__stat--fail">
          <span class="trace__stat-label">今日失败</span>
          <span class="trace__stat-value">{{ summary ? summary.todayFailed : '—' }}</span>
        </div>
        <div class="trace__stat trace__stat--ok">
          <span class="trace__stat-label">今日成功</span>
          <span class="trace__stat-value">{{ summary ? todaySuccess : '—' }}</span>
        </div>
      </section>

      <div class="trace__filters">
        <el-select
          v-model="sceneFilter"
          class="trace__filter-select"
          clearable
          placeholder="全部场景"
          aria-label="场景筛选"
        >
          <el-option
            v-for="(label, scene) in SCENE_LABELS"
            :key="scene"
            :label="label"
            :value="scene"
          />
        </el-select>
        <el-select
          v-model="resultFilter"
          class="trace__filter-select"
          placeholder="全部结果"
          aria-label="结果筛选"
        >
          <el-option label="全部结果" value="" />
          <el-option label="成功" value="true" />
          <el-option label="失败" value="false" />
        </el-select>
        <el-button type="primary" plain @click="applyFilter">筛选</el-button>
      </div>
    </div>

    <div v-if="loading" class="trace__state" role="status">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="loadError" class="trace__state">
      <p>加载失败，请稍后重试。</p>
      <el-button type="primary" plain @click="retry">重试</el-button>
    </div>
    <div v-else-if="records.length === 0" class="trace__state">
      <el-icon class="trace__state-icon"><Monitor /></el-icon>
      <p>暂无调用记录</p>
    </div>
    <template v-else>
      <el-table
        :data="records"
        class="trace__table"
        :max-height="620"
        :row-class-name="(data: any) => (data.row.success ? '' : 'trace__row-failed')"
        :header-cell-style="{ background: 'var(--xl-bg-secondary)' }"
      >
        <el-table-column label="时间" min-width="130">
          <template #default="{ row }">
            <span class="trace__cell-time">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="场景" min-width="90">
          <template #default="{ row }">{{ SCENE_LABELS[row.scene] ?? row.scene }}</template>
        </el-table-column>
        <el-table-column label="供应商" min-width="110">
          <template #default="{ row }">{{ row.provider || '—' }}</template>
        </el-table-column>
        <el-table-column label="模型" min-width="150">
          <template #default="{ row }">{{ row.model || '—' }}</template>
        </el-table-column>
        <el-table-column label="Prompt 版本" min-width="110">
          <template #default="{ row }">{{ row.promptVersion || '—' }}</template>
        </el-table-column>
        <el-table-column label="输入 Tokens" width="100" align="right">
          <template #default="{ row }">{{ row.tokensIn }}</template>
        </el-table-column>
        <el-table-column label="输出 Tokens" width="100" align="right">
          <template #default="{ row }">{{ row.tokensOut }}</template>
        </el-table-column>
        <el-table-column label="费用" width="100" align="right">
          <template #default="{ row }">{{ formatCost(row.estCost) }}</template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100" align="right">
          <template #default="{ row }">{{ row.latencyMs }}</template>
        </el-table-column>
        <el-table-column label="是否降级" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.degraded" type="warning" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否成功" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.success" type="success" size="small">成功</el-tag>
            <el-tag v-else type="danger" size="small">失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '—' }}</template>
        </el-table-column>
      </el-table>

      <nav class="trace__pagination" aria-label="分页">
        <el-pagination
          :current-page="pageNo"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="(page: number) => load(page)"
        />
      </nav>
    </template>
  </main>
</template>

<style scoped>
.trace {
  width: 100%;
  padding: var(--xl-space-8) var(--xl-content-pad);
}

.trace__title {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: 24px;
}

/* A03 顶部：统计带 + 筛选同一水平线 */
.trace__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--xl-space-4);
  margin-bottom: var(--xl-space-4);
}

/* 连续统计带：细分隔线而非三张卡 */
.trace__stats {
  display: flex;
  align-items: center;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow: hidden;
}

.trace__stat {
  display: flex;
  align-items: baseline;
  gap: var(--xl-space-2);
  padding: var(--xl-space-3) var(--xl-space-6);
}

.trace__stat + .trace__stat {
  border-left: 1px solid var(--xl-border);
}

.trace__stat-label {
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.trace__stat-value {
  color: var(--xl-text-primary);
  font-size: 26px;
  font-weight: 600;
  line-height: 1;
}

.trace__stat--ok .trace__stat-value {
  color: var(--xl-color-success);
}

.trace__stat--fail .trace__stat-value {
  color: var(--xl-color-danger);
}

.trace__filters {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
}

.trace__filter-select {
  width: 150px;
}

.trace__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.trace__state-icon {
  display: block;
  margin-bottom: var(--xl-space-3);
  font-size: 42px;
  color: var(--xl-text-muted);
}

.trace__state p {
  margin: 0;
}

.trace__state :deep(.el-skeleton) {
  text-align: left;
}

.trace__table {
  width: 100%;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow: hidden;
}

.trace__table :deep(th.el-table__cell) {
  color: var(--xl-text-secondary);
  font-weight: 600;
}

/* A03 失败行：左侧 Danger 细线 */
.trace__table :deep(.el-table__row.trace__row-failed td.el-table__cell:first-child) {
  box-shadow: inset 3px 0 0 var(--xl-color-danger);
}

.trace__cell-time {
  white-space: nowrap;
}

.trace__pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--xl-space-6);
}
</style>
