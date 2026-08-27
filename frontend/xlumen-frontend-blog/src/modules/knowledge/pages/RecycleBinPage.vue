<script setup lang="ts">
// 回收站（B21，KB-3 后端能力）：知识库/知识统一回收站，190px 分类轨（全部/知识库/知识）+ 表格。
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
    const params: { type?: 'kb' | 'knowledge'; pageNo: number; pageSize: number } = {
      pageNo,
      pageSize,
    }
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

/** 分类切换：设置类别后触发重载（单表达式，避免模板内联多语句被格式化破坏）。 */
function selectTab(tab: RecycleTab): void {
  activeTab.value = tab
  onTabChange()
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
    <div class="recycle-bin__layout">
      <aside class="recycle-bin__rail">
        <h1 class="recycle-bin__title">回收站</h1>
        <p class="recycle-bin__intro">删除的知识库和知识保留 30 天，可在到期前恢复</p>
        <nav class="recycle-bin__tabs" aria-label="回收站分类">
          <button
            type="button"
            class="recycle-bin__tab"
            :class="{ 'recycle-bin__tab--active': activeTab === 'all' }"
            @click="selectTab('all')"
          >
            全部
          </button>
          <button
            type="button"
            class="recycle-bin__tab"
            :class="{ 'recycle-bin__tab--active': activeTab === 'kb' }"
            @click="selectTab('kb')"
          >
            知识库
          </button>
          <button
            type="button"
            class="recycle-bin__tab"
            :class="{ 'recycle-bin__tab--active': activeTab === 'knowledge' }"
            @click="selectTab('knowledge')"
          >
            知识
          </button>
        </nav>
      </aside>

      <section class="recycle-bin__main">
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
          <div v-if="infinite.loadingMore" class="recycle-bin__load-more" role="status">
            加载更多…
          </div>
          <div v-else-if="infinite.loadMoreError" class="recycle-bin__load-more">
            <el-button type="primary" plain size="small" @click="infinite.retryMore()"
              >重试加载</el-button
            >
          </div>
          <div v-else-if="!infinite.hasMore" class="recycle-bin__load-more">
            已加载全部回收站条目
          </div>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.recycle-bin {
  max-width: 1040px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.recycle-bin__layout {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: var(--xl-space-6);
  align-items: start;
}

.recycle-bin__rail {
  position: sticky;
  top: calc(var(--xl-header-h) + var(--xl-space-4));
}

.recycle-bin__title {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h2);
  font-weight: var(--xl-fs-h2-w);
  letter-spacing: var(--xl-fs-h2-track);
}

.recycle-bin__intro {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.6;
}

.recycle-bin__tabs {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-1);
}

.recycle-bin__tab {
  display: block;
  width: 100%;
  padding: var(--xl-space-2) var(--xl-space-3);
  border: none;
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--xl-transition),
    color var(--xl-transition);
}

.recycle-bin__tab:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
}

.recycle-bin__tab--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-weight: var(--xl-fs-title-w);
}

.recycle-bin__main {
  min-width: 0;
}

.recycle-bin__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.recycle-bin__sentinel {
  height: 1px;
}

.recycle-bin__load-more {
  min-height: 34px;
  padding: var(--xl-space-4) 0 var(--xl-space-1);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
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
  font-size: var(--xl-fs-caption);
  overflow-wrap: break-word;
}

.recycle-bin__expired {
  color: var(--xl-color-danger);
  font-weight: var(--xl-fs-title-w);
}

@media (width <= 760px) {
  .recycle-bin__layout {
    grid-template-columns: 1fr;
  }

  .recycle-bin__rail {
    position: static;
  }

  .recycle-bin__tabs {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
