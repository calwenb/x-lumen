<script setup lang="ts">
// 搜索/分类/标签页（B03，F-0202）：关键词 + 分类 + 标签组合筛选，服务端分页，命中高亮。
// 关键状态：搜索中骨架、无结果空态（清空筛选建议）、失败可重试；搜索结果仅含公开文章（F-0307 由后端保证）。
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchArticles, fetchCategories, fetchTags } from '@/modules/publishing/api/public'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { ArticleCard, CategoryCount } from '@/modules/publishing/api/public'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const keyword = ref((route.query.keyword as string | undefined) ?? '')
const category = ref((route.query.category as string | undefined) ?? '')
const tag = ref((route.query.tag as string | undefined) ?? '')
const categories = ref<CategoryCount[]>([])
const tags = ref<CategoryCount[]>([])

const results = ref<ArticleCard[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)

function formatDate(iso: string): string {
  return iso.slice(0, 10)
}

/** 命中高亮：将 keyword 出现处包裹 <mark>（先转义防注入，再替换）。 */
function highlight(text: string, query: string): string {
  const q = query.trim()
  if (!q || !text) return escapeHtml(text)
  const escaped = escapeHtml(text)
  const pattern = escapeRegExp(q)
  return escaped.replace(new RegExp(pattern, 'gi'), (match) => `<mark>${match}</mark>`)
}

function escapeHtml(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 提交筛选：更新 URL 查询参数（组合筛选的单一事实源）。 */
function applyFilters(): void {
  const query: Record<string, string> = {}
  const k = keyword.value.trim()
  if (k) query.keyword = k
  if (category.value) query.category = category.value
  if (tag.value) query.tag = tag.value
  void router.push({ name: 'search', query })
}

async function load(targetPage: number): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchArticles({
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(category.value ? { category: category.value } : {}),
      ...(tag.value ? { tag: tag.value } : {}),
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    })
    results.value = page.records
    total.value = page.total
    pageNo.value = page.pageNo
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// 从 URL 恢复筛选状态并查询（顶栏搜索框与分类/标签链接共用本页）
watch(
  () => route.query,
  (query) => {
    keyword.value = (query.keyword as string | undefined) ?? ''
    category.value = (query.category as string | undefined) ?? ''
    tag.value = (query.tag as string | undefined) ?? ''
    void load(1)
  },
)

onMounted(async () => {
  await Promise.all([
    load(1),
    fetchCategories().then((list) => (categories.value = list)),
    fetchTags().then((list) => (tags.value = list)),
  ])
})
</script>

<template>
  <main class="search">
    <form class="search__form" @submit.prevent="applyFilters">
      <el-input
        v-model="keyword"
        class="search__input"
        placeholder="搜索文章标题或摘要…"
        aria-label="搜索关键词"
        clearable
      />
      <el-select v-model="category" class="search__select" aria-label="按分类筛选">
        <el-option value="" label="全部分类" />
        <el-option
          v-for="item in categories"
          :key="item.name"
          :value="item.name"
          :label="`${item.name}（${item.count}）`"
        />
      </el-select>
      <el-select v-model="tag" class="search__select" aria-label="按标签筛选">
        <el-option value="" label="全部标签" />
        <el-option
          v-for="item in tags"
          :key="item.name"
          :value="item.name"
          :label="`${item.name}（${item.count}）`"
        />
      </el-select>
      <el-button type="primary" native-type="submit" class="search__submit">搜索</el-button>
    </form>

    <div v-if="loading" class="search__state">
      <div v-for="i in 3" :key="i" class="search__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="search__state">
      <p class="search__state-text">搜索失败</p>
      <el-button type="primary" plain @click="load(pageNo)">重试</el-button>
    </div>
    <div v-else-if="results.length === 0" class="search__state">
      <p class="search__state-text">没有找到相关文章，试试清空筛选或更换关键词。</p>
      <RouterLink class="search__reset" to="/search">清空筛选</RouterLink>
    </div>
    <template v-else>
      <p class="search__summary">共 {{ total }} 篇相关文章</p>
      <article v-for="article in results" :key="article.id" class="search-card">
        <RouterLink class="search-card__title" :to="`/articles/${article.id}`">
          <span v-html="highlight(article.title, keyword)" />
        </RouterLink>
        <p class="search-card__summary" v-html="highlight(article.summary, keyword)" />
        <div class="search-card__meta">
          <span v-if="article.category">{{ article.category }}</span>
          <span>{{ formatDate(article.publishedAt) }}</span>
          <span>{{ article.readMinutes }} 分钟阅读</span>
        </div>
      </article>
      <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @change="load" />
    </template>
  </main>
</template>

<style scoped>
.search {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.search__form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-4);
}

.search__input {
  flex: 1;
  min-width: 180px;
}

.search__select {
  width: 150px;
}

.search__submit {
  flex-shrink: 0;
}

.search__state {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-3);
  align-items: center;
  padding: var(--xl-space-8) 0;
}

.search__skeleton {
  width: 100%;
  height: 90px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.search__state-text {
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.search__reset {
  padding: 6px 16px;
  border: 1px solid var(--xl-color-primary);
  border-radius: 8px;
  background: transparent;
  color: var(--xl-color-primary);
  font-size: 13px;
  text-decoration: none;
}

.search__reset:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
}

.search__summary {
  color: var(--xl-text-muted);
  font-size: 13px;
}

.search-card {
  padding: var(--xl-space-4) var(--xl-space-6);
  margin-bottom: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.search-card:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.search-card__title {
  color: var(--xl-text-primary);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
}

.search-card__title:hover {
  color: var(--xl-color-primary);
}

.search-card__summary {
  margin: var(--xl-space-2) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.search-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: 12px;
}

:deep(mark) {
  padding: 0 2px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--xl-color-primary) 18%, transparent);
  color: inherit;
}
</style>
