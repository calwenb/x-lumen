<script setup lang="ts">
// 文章列表页（B10，F-0301）：作者文章管理（状态/可见性/关键词筛选 + 新建/编辑/删除）。
// 关键状态：加载骨架、空态（引导新建）、失败可重试；删除仅构思/草稿可用（已发布需先下架）。
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { deleteArticle, fetchArticles, STATUS_LABELS, VISIBILITY_LABELS } from '@/modules/content/api/article'
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
  if (!window.confirm(`确定删除「${item.title}」吗？删除后不可恢复。`)) {
    return
  }
  try {
    await deleteArticle(item.id)
    await load(pageNo.value)
  } catch {
    window.alert('删除失败，仅构思/草稿状态的文章可删除')
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
      <select v-model="filterStatus" class="article-list__select" aria-label="状态筛选">
        <option value="">全部状态</option>
        <option v-for="(label, value) in STATUS_LABELS" :key="value" :value="String(value)">{{ label }}</option>
      </select>
      <select v-model="filterVisibility" class="article-list__select" aria-label="可见性筛选">
        <option value="">全部可见性</option>
        <option v-for="(label, value) in VISIBILITY_LABELS" :key="value" :value="String(value)">{{ label }}</option>
      </select>
      <input
        v-model="keyword"
        class="article-list__keyword"
        type="search"
        placeholder="搜索标题"
        aria-label="搜索标题"
        @keyup.enter="applyFilters"
      />
      <button type="button" class="article-list__filter-button" @click="applyFilters">筛选</button>
    </div>

    <div v-if="loading" class="article-list__skeleton" role="status">加载中…</div>
    <div v-else-if="loadError" class="article-list__error">
      <p>加载失败，请稍后重试。</p>
      <button type="button" class="article-list__retry" @click="load()">重试</button>
    </div>
    <div v-else-if="articles.length === 0" class="article-list__empty">
      <p>还没有文章，点击右上角「新建文章」开始创作。</p>
    </div>
    <template v-else>
      <ul class="article-list__items">
        <li v-for="item in articles" :key="item.id" class="article-list__item">
          <div class="article-list__item-main">
            <RouterLink class="article-list__item-title" :to="{ name: 'article-edit', params: { id: item.id } }">
              {{ item.title }}
            </RouterLink>
            <div class="article-list__item-meta">
              <span class="article-list__badge">{{ STATUS_LABELS[item.status] ?? item.status }}</span>
              <span class="article-list__badge" :class="{ 'article-list__badge--private': item.visibility === 0 }">
                {{ VISIBILITY_LABELS[item.visibility] ?? item.visibility }}
              </span>
              <span v-if="item.category" class="article-list__item-category">{{ item.category }}</span>
              <span class="article-list__item-time">{{ formatTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div class="article-list__item-actions">
            <RouterLink class="article-list__action" :to="{ name: 'article-edit', params: { id: item.id } }">
              编辑
            </RouterLink>
            <button
              type="button"
              class="article-list__action article-list__action--danger"
              :disabled="item.status !== 1 && item.status !== 2"
              @click="handleDelete(item)"
            >
              删除
            </button>
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
  border-radius: var(--xl-radius-sm, 6px);
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
  gap: 10px;
  margin-bottom: 20px;
}

.article-list__select,
.article-list__keyword {
  padding: 7px 10px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-size: 13px;
}

.article-list__keyword {
  flex: 1;
  max-width: 240px;
}

.article-list__filter-button {
  padding: 7px 16px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm, 6px);
  background: transparent;
  color: var(--xl-color-primary);
  font-size: 13px;
  cursor: pointer;
}

.article-list__skeleton,
.article-list__error,
.article-list__empty {
  padding: 48px 0;
  text-align: center;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.article-list__retry {
  margin-top: 12px;
  padding: 6px 18px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius-sm, 6px);
  background: transparent;
  color: var(--xl-color-primary);
  cursor: pointer;
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
  padding: 16px 18px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
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
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--xl-text-secondary);
}

.article-list__badge {
  padding: 2px 8px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.article-list__badge--private {
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
}

.article-list__item-actions {
  display: flex;
  gap: 8px;
}

.article-list__action {
  padding: 5px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: transparent;
  color: var(--xl-text-primary);
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.article-list__action--danger {
  color: var(--xl-color-danger, #d03050);
}

.article-list__action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
