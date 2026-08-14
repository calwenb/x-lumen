<script setup lang="ts">
// 文章列表页（B10，F-0301）：作者文章管理（状态/可见性/关键词筛选 + 新建/编辑/删除）。
// 关键状态：加载骨架、空态（引导新建）、失败可重试；删除仅构思/草稿可用（已发布需先下架）。
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'

import {
  deleteArticle,
  fetchArticles,
  STATUS_LABELS,
  VISIBILITY_LABELS,
} from '@/modules/content/api/article'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { ArticleListItem } from '@/modules/content/api/article'

const PAGE_SIZE = 10

const articles = ref<ArticleListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(true)
const loadError = ref(false)

const filterStatus = ref('')
const filterVisibility = ref('')
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
    const page = await fetchArticles({
      ...(filterStatus.value ? { status: Number(filterStatus.value) } : {}),
      ...(filterVisibility.value ? { visibility: Number(filterVisibility.value) } : {}),
      ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    })
    articles.value = page.records
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

async function handleDelete(item: ArticleListItem): Promise<void> {
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
    await deleteArticle(item.id)
    ElMessage.success('已删除')
    await load(pageNo.value)
  } catch {
    ElMessage.error('删除失败，仅构思/草稿状态的文章可删除')
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="article-list">
    <div class="article-list__header">
      <h1 class="article-list__title">文章管理</h1>
      <RouterLink class="article-list__create" :to="{ name: 'article-new' }">新建文章</RouterLink>
    </div>

    <div class="article-list__filters">
      <el-select
        v-model="filterStatus"
        class="article-list__select"
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
      <el-select
        v-model="filterVisibility"
        class="article-list__select"
        aria-label="可见性筛选"
        placeholder="全部可见性"
      >
        <el-option value="" label="全部可见性" />
        <el-option
          v-for="(label, value) in VISIBILITY_LABELS"
          :key="value"
          :value="String(value)"
          :label="label"
        />
      </el-select>
      <el-input
        v-model="keyword"
        class="article-list__keyword"
        placeholder="搜索标题"
        aria-label="搜索标题"
        clearable
        @keyup.enter="applyFilters"
      />
      <el-button type="primary" plain @click="applyFilters">筛选</el-button>
    </div>

    <div v-if="loading" class="article-list__state" role="status">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="loadError" class="article-list__state">
      <p>加载失败，请稍后重试。</p>
      <el-button type="primary" plain @click="load()">重试</el-button>
    </div>
    <div v-else-if="articles.length === 0" class="article-list__state">
      <el-icon class="article-list__state-icon"><Document /></el-icon>
      <p>还没有文章，点击右上角「新建文章」开始创作。</p>
    </div>
    <template v-else>
      <ul class="article-list__items">
        <li v-for="item in articles" :key="item.id" class="article-list__item">
          <div class="article-list__item-main">
            <RouterLink
              class="article-list__item-title"
              :to="{ name: 'article-edit', params: { id: item.id } }"
            >
              {{ item.title }}
            </RouterLink>
            <div class="article-list__item-meta">
              <el-tag :type="statusTagType(item.status)" size="small" effect="light">
                {{ STATUS_LABELS[item.status] ?? item.status }}
              </el-tag>
              <el-tag
                :type="item.visibility === 0 ? 'info' : 'success'"
                size="small"
                effect="light"
              >
                {{ VISIBILITY_LABELS[item.visibility] ?? item.visibility }}
              </el-tag>
              <span v-if="item.category" class="article-list__item-category">{{
                item.category
              }}</span>
              <span class="article-list__item-time">{{ formatTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div class="article-list__item-actions">
            <RouterLink
              class="article-list__action"
              :to="{ name: 'article-edit', params: { id: item.id } }"
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
.article-list {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.article-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.article-list__title {
  margin: 0;
  font-size: 24px;
}

.article-list__create {
  padding: 8px 18px;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}

.article-list__create:hover {
  background: var(--xl-color-primary-hover);
}

.article-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.article-list__select {
  width: 130px;
}

.article-list__keyword {
  flex: 1;
  max-width: 240px;
}

.article-list__state {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.article-list__state-icon {
  display: block;
  margin-bottom: var(--xl-space-3);
  font-size: 40px;
  color: var(--xl-text-muted);
}

.article-list__state p {
  margin: 0;
}

.article-list__state :deep(.el-skeleton) {
  text-align: left;
}

.article-list__items {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.article-list__item {
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

.article-list__item:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-1px);
}

.article-list__item-title {
  color: var(--xl-text-primary);
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
}

.article-list__item-title:hover {
  color: var(--xl-color-primary);
}

.article-list__item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--xl-text-secondary);
}

.article-list__item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.article-list__action {
  padding: 5px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: transparent;
  color: var(--xl-text-primary);
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.article-list__action:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}
</style>
