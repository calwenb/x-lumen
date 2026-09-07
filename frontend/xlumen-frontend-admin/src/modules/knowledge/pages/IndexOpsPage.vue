<script setup lang="ts">
// 索引维护：全平台向量索引补跑（触发 + 3s 轮询进度 + 失败明细）。
// 场景：Milvus 停机降级期间发布的知识只落了元数据无向量，服务恢复后一次补跑全空间存量。
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

import { fetchReindexPlatformStatus, triggerReindexAllPlatform } from '../api/indexOps'

import type { ReindexPlatformProgress } from '../api/indexOps'

/** 运行中轮询间隔（毫秒）。 */
const POLL_INTERVAL_MS = 3000

const progress = ref<ReindexPlatformProgress | null>(null)
const loading = ref(true)
const loadError = ref(false)
const triggering = ref(false)

let pollTimer: ReturnType<typeof setInterval> | null = null

const running = computed(() => progress.value?.running === true)
const finished = computed(() => !running.value && progress.value?.startedAt != null)
const neverTriggered = computed(() => progress.value != null && progress.value.startedAt == null)

/** 进度百分比（total=0 时给 0，防除零）。 */
const percent = computed(() => {
  const p = progress.value
  if (!p || p.total <= 0) {
    return 0
  }
  return Math.min(100, Math.round((p.processed / p.total) * 100))
})

function statusTag(): { text: string; type: 'success' | 'info' | 'warning' | 'danger' } {
  if (running.value) {
    return { text: '补跑中', type: 'success' }
  }
  if (finished.value) {
    return progress.value && progress.value.failedCount > 0
      ? { text: '已完成（有失败）', type: 'warning' }
      : { text: '已完成', type: 'info' }
  }
  return { text: '未触发', type: 'info' }
}

function formatTime(iso: string | null): string {
  return iso ? iso.slice(0, 19).replace('T', ' ') : '—'
}

function startPolling(): void {
  if (pollTimer != null) {
    return
  }
  pollTimer = setInterval(() => void refreshStatus(true), POLL_INTERVAL_MS)
}

function stopPolling(): void {
  if (pollTimer != null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 拉取进度；silent=轮询失败不打断页面（只在手动/首载时亮错误态）。 */
async function refreshStatus(silent = false): Promise<void> {
  if (!silent) {
    loading.value = true
    loadError.value = false
  }
  try {
    const next = await fetchReindexPlatformStatus()
    progress.value = next
    if (next.running) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch {
    if (!silent) {
      loadError.value = true
    }
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

/** 触发补跑：先确认（付费 embedding + 全量重建），服务端保证单任务不并跑。 */
async function confirmTrigger(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '将遍历所有空间的已发布知识，逐条强制重建向量索引；每条会实际调用 Embedding 模型（产生费用），任务在后台单线程串行执行。确认开始？',
      '全平台索引补跑',
      { type: 'warning', confirmButtonText: '开始补跑', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  triggering.value = true
  try {
    const next = await triggerReindexAllPlatform()
    progress.value = next
    if (next.running) {
      ElMessage.info(next.started ? '补跑任务已启动' : '已有补跑任务在运行，展示当前进度')
      startPolling()
    } else if (next.failedCount > 0) {
      ElMessage.warning(`补跑已结束：成功 ${next.ok} 条，失败 ${next.failedCount} 条`)
    } else {
      ElMessage.success(`补跑已完成：成功 ${next.ok} 条`)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '触发失败，请稍后重试')
  } finally {
    triggering.value = false
  }
}

function retry(): void {
  void refreshStatus()
}

onMounted(() => void refreshStatus())
onUnmounted(stopPolling)
</script>

<template>
  <main class="iop">
    <h1 class="iop__title">索引维护</h1>

    <el-alert
      class="iop__alert"
      type="info"
      :closable="false"
      show-icon
      title="补跑会遍历全平台已发布知识、逐条重建向量索引。执行前请确认向量库可达（后端启动日志出现「Milvus 可达」）；若处于 Noop 降级，补跑只会重写元数据、不产生向量。"
    />

    <div class="iop__top">
      <section class="iop__stats" aria-label="补跑进度统计">
        <div class="iop__stat">
          <span class="iop__stat-label">可重建知识</span>
          <span class="iop__stat-value">{{ progress ? progress.total : '—' }}</span>
        </div>
        <div class="iop__stat">
          <span class="iop__stat-label">已处理</span>
          <span class="iop__stat-value">{{ progress ? progress.processed : '—' }}</span>
        </div>
        <div class="iop__stat iop__stat--ok">
          <span class="iop__stat-label">成功</span>
          <span class="iop__stat-value">{{ progress ? progress.ok : '—' }}</span>
        </div>
        <div class="iop__stat iop__stat--fail">
          <span class="iop__stat-label">失败</span>
          <span class="iop__stat-value">{{ progress ? progress.failedCount : '—' }}</span>
        </div>
      </section>

      <div class="iop__actions">
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="triggering || running"
          @click="confirmTrigger"
        >
          {{ running ? '补跑中…' : '开始全平台补跑' }}
        </el-button>
        <el-button :loading="loading" @click="retry">刷新进度</el-button>
      </div>
    </div>

    <p v-if="loadError" class="iop__error">
      加载进度失败，请检查登录状态或稍后重试。
      <el-button link type="primary" @click="retry">重试</el-button>
    </p>

    <el-skeleton v-else-if="loading" :rows="3" animated />

    <template v-else-if="progress">
      <section class="iop__panel">
        <div class="iop__panel-head">
          <span class="iop__panel-title">当前任务</span>
          <el-tag :type="statusTag().type" size="small" effect="light">{{
            statusTag().text
          }}</el-tag>
          <span v-if="neverTriggered" class="iop__hint"
            >服务端尚无任务记录（重启后进度清空，属正常）</span
          >
        </div>
        <el-progress
          v-if="!neverTriggered"
          class="iop__progress"
          :percentage="percent"
          :status="running ? undefined : percent >= 100 ? 'success' : 'exception'"
          striped
          :striped-flow="running"
        />
        <dl v-if="!neverTriggered" class="iop__meta">
          <div>
            <dt>开始</dt>
            <dd>{{ formatTime(progress.startedAt) }}</dd>
          </div>
          <div>
            <dt>结束</dt>
            <dd>{{ formatTime(progress.finishedAt) }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="progress.failed.length > 0" class="iop__panel">
        <div class="iop__panel-head">
          <span class="iop__panel-title">失败明细</span>
          <span class="iop__hint">最多展示前 100 条，完整计数见上方「失败」</span>
        </div>
        <el-table :data="progress.failed" size="small" max-height="360">
          <el-table-column prop="knowledgeId" label="知识 ID" width="200" />
          <el-table-column prop="reason" label="原因" min-width="280" show-overflow-tooltip />
        </el-table>
      </section>
    </template>
  </main>
</template>

<style scoped>
.iop {
  padding: var(--xl-space-8) var(--xl-space-10);
  max-width: 1080px;
}

.iop__title {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 24px;
  font-weight: 650;
}

.iop__alert {
  margin-bottom: var(--xl-space-6);
}

.iop__top {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--xl-space-6);
  margin-bottom: var(--xl-space-6);
  flex-wrap: wrap;
}

.iop__stats {
  display: flex;
  gap: var(--xl-space-8);
  padding: var(--xl-space-4) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  background: var(--xl-bg-surface);
}

.iop__stat {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-1);
}

.iop__stat-label {
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.iop__stat-value {
  color: var(--xl-text-primary);
  font-size: 22px;
  font-weight: 650;
}

.iop__stat--ok .iop__stat-value {
  color: var(--el-color-success);
}

.iop__stat--fail .iop__stat-value {
  color: var(--el-color-danger);
}

.iop__actions {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
}

.iop__error {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 14px;
}

.iop__panel {
  margin-bottom: var(--xl-space-6);
  padding: var(--xl-space-4) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  background: var(--xl-bg-surface);
}

.iop__panel-head {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  margin-bottom: var(--xl-space-3);
}

.iop__panel-title {
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
  font-weight: 600;
}

.iop__hint {
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.iop__progress {
  margin-bottom: var(--xl-space-3);
}

.iop__meta {
  display: flex;
  gap: var(--xl-space-8);
  margin: 0;
  font-size: 13px;
}

.iop__meta dt {
  display: inline;
  color: var(--xl-text-secondary);
}

.iop__meta dd {
  display: inline;
  margin: 0 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
}
</style>
