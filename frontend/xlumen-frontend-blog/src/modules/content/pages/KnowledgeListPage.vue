<script setup lang="ts">
// 知识列表页（B10，F-0301，KB-4 适配决策 D16）：作者知识管理（状态/关键词筛选 + 新建/编辑/删除/提交审核）。
// 关键状态：加载骨架、空态（引导新建）、失败可重试；删除仅构思/草稿可用（已发布需先下架）。
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'

import { deleteKnowledge, fetchKnowledges, STATUS_LABELS } from '@/modules/content/api/knowledge'
import { createReview } from '@/modules/publishing/api/review'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { KnowledgeListItem } from '@/modules/content/api/knowledge'

const PAGE_SIZE = 10

const knowledges = ref<KnowledgeListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)
const submittingId = ref<string | null>(null)

const filterStatus = ref('')
const keyword = ref('')

/** 状态徽标语义色（el-tag type）：未发布灰/已发布绿/其他主色。 */
function statusTagType(status: number): 'success' | 'primary' | 'info' {
  if (status === 6) return 'success'
  if (status === 8) return 'info'
  return 'primary'
}

/** 日期显示：yyyy-MM-dd HH:mm。 */
function formatTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

async function load(targetPage = pageNo.value): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchKnowledges({
      ...(filterStatus.value ? { status: Number(filterStatus.value) } : {}),
      ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    })
    knowledges.value = page.records
    total.value = page.total
    pageNo.value = targetPage
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function applyFilters(): void {
  void load(1)
}

async function handleDelete(item: KnowledgeListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${item.title}」吗？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消删除
    return
  }
  try {
    await deleteKnowledge(item.id)
    ElMessage.success('已删除')
    await load(pageNo.value)
  } catch {
    ElMessage.error('删除失败，仅构思/草稿状态的知识可删除')
  }
}

/** 提交审核（F-0902）：草稿/已通过态可用（BUG-5 列表入口补全）。 */
async function handleSubmitReview(item: KnowledgeListItem): Promise<void> {
  submittingId.value = item.id
  try {
    await createReview(item.id)
    ElMessage.success('已提交审核')
    await load(pageNo.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审核失败')
  } finally {
    submittingId.value = null
  }
}

/** 草稿（2）或已通过（4）可提交审核。 */
function canSubmitReview(status: number): boolean {
  return status === 2 || status === 4
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="knowledge-list">
    <div class="knowledge-list__header">
      <h1 class="knowledge-list__title">知识管理</h1>
      <RouterLink class="knowledge-list__create" :to="{ name: 'knowledge-new' }"
        >新建知识</RouterLink
      >
    </div>

    <div class="knowledge-list__filters">
      <el-select
        v-model="filterStatus"
        class="knowledge-list__select"
        aria-label="状态筛选"
        placeholder="全部状态"
      >
        <el-option value="" label="全部状态" />
        <el-option
          v-for="(label, value) in STATUS_LABELS"
          :key="value"
          :value="String(value)"
          :label="label"
        />
      </el-select>
      <el-input
        v-model="keyword"
        class="knowledge-list__keyword"
        placeholder="搜索标题"
        aria-label="搜索标题"
        clearable
        @keyup.enter="applyFilters"
      />
      <el-button type="primary" plain @click="applyFilters">筛选</el-button>
    </div>

    <div v-if="loading" class="knowledge-list__state" role="status">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="loadError" class="knowledge-list__state">
      <p>加载失败，请稍后重试。</p>
      <el-button type="primary" plain @click="load()">重试</el-button>
    </div>
    <div v-else-if="knowledges.length === 0" class="knowledge-list__state">
      <el-icon class="knowledge-list__state-icon"><Document /></el-icon>
      <p>还没有知识，点击右上角「新建知识」开始创作。</p>
    </div>
    <template v-else>
      <ul class="knowledge-list__items">
        <li v-for="item in knowledges" :key="item.id" class="knowledge-list__item">
          <div class="knowledge-list__item-main">
            <RouterLink
              class="knowledge-list__item-title"
              :to="{ name: 'knowledge-edit', params: { id: item.id } }"
            >
              {{ item.title }}
            </RouterLink>
            <div class="knowledge-list__item-meta">
              <el-tag :type="statusTagType(item.status)" size="small" effect="light">
                {{ STATUS_LABELS[item.status] ?? item.status }}
              </el-tag>
              <span class="knowledge-list__item-time">{{ formatTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div class="knowledge-list__item-actions">
            <el-button
              v-if="canSubmitReview(item.status)"
              type="success"
              plain
              size="small"
              :loading="submittingId === item.id"
              @click="handleSubmitReview(item)"
            >
              提交审核
            </el-button>
            <RouterLink
              class="knowledge-list__action"
              :to="{ name: 'knowledge-edit', params: { id: item.id } }"
            >
              编辑
            </RouterLink>
            <el-button
              type="danger"
              plain
              size="small"
              :disabled="item.status !== 1 && item.status !== 2"
              @click="handleDelete(item)"
            >
              删除
            </el-button>
          </div>
        </li>
      </ul>
      <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @change="load" />
    </template>
  </main>
</template>

<style scoped>
.knowledge-list {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.knowledge-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.knowledge-list__title {
  margin: 0;
  font-size: 24px;
}

.knowledge-list__create {
  padding: 8px 18px;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}

.knowledge-list__create:hover {
  background: var(--xl-color-primary-hover);
}

.knowledge-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.knowledge-list__select {
  width: 130px;
}

.knowledge-list__keyword {
  flex: 1;
  max-width: 240px;
}

.knowledge-list__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.knowledge-list__state-icon {
  display: block;
  margin-bottom: var(--xl-space-3);
  font-size: 40px;
  color: var(--xl-text-muted);
}

.knowledge-list__state p {
  margin: 0;
}

.knowledge-list__state :deep(.el-skeleton) {
  text-align: left;
}

.knowledge-list__items {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.knowledge-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-4);
  padding: 16px 18px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.knowledge-list__item:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-1px);
}

.knowledge-list__item-title {
  color: var(--xl-text-primary);
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
}

.knowledge-list__item-title:hover {
  color: var(--xl-color-primary);
}

.knowledge-list__item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--xl-text-secondary);
}

.knowledge-list__item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.knowledge-list__action {
  padding: 5px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-text-primary);
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.knowledge-list__action:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}
</style>
