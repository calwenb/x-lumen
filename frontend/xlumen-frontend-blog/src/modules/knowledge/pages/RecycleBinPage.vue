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
import Pagination from '@/modules/publishing/components/Pagination.vue'

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
const items = ref<RecycleBinItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)

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

async function load(targetPage = pageNo.value): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    // exactOptionalPropertyTypes：type 仅在非 all 时携带，避免显式 undefined 传参
    const params: { type?: 'kb' | 'knowledge'; pageNo: number; pageSize: number } = {
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    }
    const type = toQueryType(activeTab.value)
    if (type) params.type = type
    const page = await fetchRecycleBin(params)
    items.value = page.records
    total.value = page.total
    pageNo.value = targetPage
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onTabChange(): void {
  void load(1)
}

function onPageChange(target: number): void {
  void load(target)
}

async function restore(item: RecycleBinItem): Promise<void> {
  try {
    await restoreRecycleBinItem(item.type, item.id)
    ElMessage.success(`「${item.name}」已恢复`)
    await load()
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
    // 当前页删空则回退一页
    await load(
      total.value > 1 && items.value.length === 1 ? Math.max(1, pageNo.value - 1) : pageNo.value,
    )
  } catch (error) {
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '彻底删除失败，请稍后重试',
    )
  }
}

onMounted(() => {
  void load(1)
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
        <el-button type="primary" plain size="small" @click="load(1)">重试</el-button>
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
        <Pagination
          :page-no="pageNo"
          :page-size="PAGE_SIZE"
          :total="total"
          @change="onPageChange"
        />
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
