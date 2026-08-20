<script setup lang="ts">
// 回收站（B16，F-0305，KB-3 后端能力）：知识库/知识统一回收站，双 Tab（全部/知识库/知识）。
// 剩余天数 = deletedAt + 30 天 - now（超期自动彻底删除，后端定时清理）；恢复/彻底删除二次确认。
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  fetchRecycleBin,
  purgeRecycleBinItem,
  restoreRecycleBinItem,
} from '@/modules/knowledge/api/knowledgeBase'
import { useInfinitePage } from '@/composables/useInfinitePage'

import type { RecycleBinItem } from '@/modules/knowledge/api/knowledgeBase'

const PAGE_SIZE = 10
/** 回收站保留天数（与后端超期清理一致，PRODUCT §6）。 */
const RETENTION_DAYS = 30

type RecycleTab = 'all' | 'kb' | 'knowledge'

const TYPE_LABELS: Record<RecycleBinItem['type'], string> = {
  kb: '知识库',
  knowledge: '知识',
}

const activeTab = ref<RecycleTab>('all')
const sentinel = ref<HTMLElement | null>(null)

function toQueryType(tab: RecycleTab): 'kb' | 'knowledge' | undefined {
  return tab === 'all' ? undefined : tab
}

/** 剩余天数（不足一天按 0 天），超期显示「已过期」。 */
function daysLeft(deletedAt: string): number {
  const expireAt = new Date(deletedAt).getTime() + RETENTION_DAYS * 24 * 60 * 60 * 1000
  return Math.floor((expireAt - Date.now()) / (24 * 60 * 60 * 1000))
}

function isExpired(deletedAt: string): boolean {
  return daysLeft(deletedAt) <= 0
}

function formatTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

const infinite = useInfinitePage<RecycleBinItem>({
  sentinel,
  pageSize: PAGE_SIZE,
  loadPage: (pageNo, pageSize) => {
    const params: { type?: 'kb' | 'knowledge'; pageNo: number; pageSize: number } = { pageNo, pageSize }
    const type = toQueryType(activeTab.value)
    if (type) params.type = type
    return fetchRecycleBin(params)
  },
})

const items = infinite.items
const loading = infinite.loading
const loadError = infinite.error

function onTabChange(): void {
  void infinite.loadFirst()
}

async function restore(item: RecycleBinItem): Promise<void> {
  try {
    await restoreRecycleBinItem(item.type, item.id)
    ElMessage.success(`「${item.name}」已恢复`)
    await infinite.loadFirst()
  } catch (error) {
    // 后端 409「原知识库不存在，无法恢复」等错误文案透出
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '恢复失败，请稍后重试',
    )
  }
}

async function purge(item: RecycleBinItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`彻底删除「${item.name}」？彻底删除后不可恢复。`, '彻底删除', {
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消
    return
  }
  try {
    await purgeRecycleBinItem(item.type, item.id)
    ElMessage.success('已彻底删除')
    await infinite.loadFirst()
  } catch (error) {
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '彻底删除失败，请稍后重试',
    )
  }
}

onMounted(() => {
  void infinite.loadFirst()
})
</script>

<template>
  <main class="recycle-bin">
    <header class="recycle-bin__header">
      <h1 class="recycle-bin__title">回收站</h1>
      <p class="recycle-bin__intro">保留 30 天，超期自动彻底删除。</p>
    </header>

    <section class="recycle-bin__section">
      <el-tabs v-model="activeTab" class="recycle-bin__tabs" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="知识库" name="kb" />
        <el-tab-pane label="知识" name="knowledge" />
      </el-tabs>

      <div v-if="loading" class="recycle-bin__state">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="loadError" class="recycle-bin__state">
        <p>回收站加载失败</p>
        <el-button type="primary" plain size="small" @click="infinite.retry()">重试</el-button>
      </div>
      <div v-else-if="items.length === 0" class="recycle-bin__state">回收站空空如也。</div>
      <template v-else>
        <el-table :data="items" class="recycle-bin__table">
          <el-table-column label="名称" min-width="240">
            <template #default="{ row }: { row: RecycleBinItem }">
              <div class="recycle-bin__name">
                <span class="recycle-bin__name-text">{{ row.name }}</span>
                <span v-if="row.type === 'knowledge' && row.kbName" class="recycle-bin__name-sub">
                  所属库：{{ row.kbName
                  }}<template v-if="row.directoryName"> · {{ row.directoryName }}</template>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }: { row: RecycleBinItem }">
              <el-tag :type="row.type === 'kb' ? 'primary' : 'info'" effect="plain" size="small">
                {{ TYPE_LABELS[row.type] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" width="160">
            <template #default="{ row }: { row: RecycleBinItem }">
              {{ formatTime(row.deletedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="剩余天数" width="110">
            <template #default="{ row }: { row: RecycleBinItem }">
              <span v-if="isExpired(row.deletedAt)" class="recycle-bin__expired">已过期</span>
              <span v-else>{{ daysLeft(row.deletedAt) }} 天</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }: { row: RecycleBinItem }">
              <el-button type="primary" plain size="small" @click="restore(row)">恢复</el-button>
              <el-button type="danger" size="small" @click="purge(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div ref="sentinel" class="recycle-bin__sentinel" aria-hidden="true" />
        <div v-if="infinite.loadingMore" class="recycle-bin__load-more" role="status">加载更多…</div>
        <div v-else-if="infinite.loadMoreError" class="recycle-bin__load-more">
          <el-button type="primary" plain size="small" @click="infinite.retryMore()">重试加载</el-button>
        </div>
        <div v-else-if="!infinite.hasMore" class="recycle-bin__load-more">已加载全部回收站条目</div>
      </template>
    </section>
  </main>
</template>

<style scoped>
.recycle-bin {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.recycle-bin__header {
  margin-bottom: 24px;
}

.recycle-bin__title {
  margin: 0;
  font-size: 24px;
}

.recycle-bin__intro {
  margin: 8px 0 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.recycle-bin__section {
  margin-bottom: 32px;
}

.recycle-bin__tabs {
  margin-bottom: 4px;
}

.recycle-bin__state {
  padding: 32px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.recycle-bin__sentinel {
  height: 1px;
}

.recycle-bin__load-more {
  min-height: 34px;
  padding: 14px 0 4px;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: center;
}

.recycle-bin__state :deep(.el-skeleton) {
  text-align: left;
}

.recycle-bin__table {
  border-radius: var(--xl-radius-card);
}

.recycle-bin__name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.recycle-bin__name-text {
  overflow-wrap: break-word;
}

.recycle-bin__name-sub {
  color: var(--xl-text-muted);
  font-size: 12px;
  overflow-wrap: break-word;
}

.recycle-bin__expired {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
