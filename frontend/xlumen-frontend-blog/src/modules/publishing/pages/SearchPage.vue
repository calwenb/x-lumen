<script setup lang="ts">
// 搜索/标签页（B03，F-0202）：关键词 + 标签 + 知识库/目录组合筛选，服务端分页，命中高亮。
// 关键状态：搜索中骨架、无结果空态（清空筛选建议）、失败可重试；搜索结果仅含公开知识（F-0307 由后端保证）。
// 决策 D16：category 废弃（后端已删 /public/categories 接口与 category 参数，目录树接管），
// 知识库/目录筛选需登录（fetchKnowledgeBases/fetchDirectoryTree 为鉴权接口，未登录显示空态说明）。
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { fetchKnowledges, fetchTags } from '@/modules/publishing/api/public'
import Pagination from '@/modules/publishing/components/Pagination.vue'
import { useSessionStore } from '@/stores/session'

import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import type { CategoryCount, KnowledgeCard } from '@/modules/publishing/api/public'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const keyword = ref((route.query.keyword as string | undefined) ?? '')
const kbId = ref((route.query.kbId as string | undefined) ?? '')
const directoryId = ref((route.query.directoryId as string | undefined) ?? '')
const tag = ref((route.query.tag as string | undefined) ?? '')
const knowledgeBases = ref<KnowledgeBase[]>([])
const directories = ref<DirectoryOption[]>([])
const tags = ref<CategoryCount[]>([])

const results = ref<KnowledgeCard[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)

interface DirectoryOption {
  value: string
  label: string
}

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

/** 目录树平铺为 el-select 选项（层级以全角空格缩进展示）。 */
function flattenDirectories(nodes: DirectoryNode[], depth = 0): DirectoryOption[] {
  const options: DirectoryOption[] = []
  for (const node of nodes) {
    options.push({ value: node.id, label: `${'\u3000'.repeat(depth)}${node.name}` })
    options.push(...flattenDirectories(node.children, depth + 1))
  }
  return options
}

async function loadKnowledgeBases(): Promise<void> {
  try {
    knowledgeBases.value = await fetchKnowledgeBases()
  } catch {
    // 鉴权失败/未登录：保持空列表，仅显示空态说明，不影响搜索主流程
    knowledgeBases.value = []
  }
}

// 知识库变更：加载其目录树（URL 恢复与手选共用）；清空时同时清目录选择
watch(
  kbId,
  async (val) => {
    if (!val) {
      directories.value = []
      directoryId.value = ''
      return
    }
    if (!session.loggedIn) return
    try {
      directories.value = flattenDirectories(await fetchDirectoryTree(val))
    } catch {
      directories.value = []
    }
  },
  { immediate: true },
)

/** 提交筛选：更新 URL 查询参数（组合筛选的单一事实源）。 */
function applyFilters(): void {
  const query: Record<string, string> = {}
  const k = keyword.value.trim()
  if (k) query.keyword = k
  if (tag.value) query.tag = tag.value
  if (kbId.value) query.kbId = kbId.value
  if (kbId.value && directoryId.value) query.directoryId = directoryId.value
  void router.push({ name: 'search', query })
}

async function load(targetPage: number): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchKnowledges({
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(kbId.value ? { kbId: kbId.value } : {}),
      ...(kbId.value && directoryId.value ? { directoryId: directoryId.value } : {}),
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

// 从 URL 恢复筛选状态并查询（顶栏搜索框与标签链接共用本页）；directoryId 仅在 kbId 存在时生效
watch(
  () => route.query,
  (query) => {
    keyword.value = (query.keyword as string | undefined) ?? ''
    tag.value = (query.tag as string | undefined) ?? ''
    kbId.value = (query.kbId as string | undefined) ?? ''
    directoryId.value = kbId.value ? ((query.directoryId as string | undefined) ?? '') : ''
    void load(1)
  },
)

onMounted(async () => {
  const tasks: Promise<unknown>[] = [load(1), fetchTags().then((list) => (tags.value = list))]
  if (session.loggedIn) tasks.push(loadKnowledgeBases())
  await Promise.all(tasks)
})
</script>

<template>
  <main class="search">
    <form class="search__form" @submit.prevent="applyFilters">
      <el-input
        v-model="keyword"
        class="search__input"
        placeholder="搜索知识标题或摘要…"
        aria-label="搜索关键词"
        clearable
      />
      <el-select v-model="kbId" class="search__select" aria-label="按知识库筛选">
        <el-option value="" label="全部知识库" />
        <el-option
          v-for="item in knowledgeBases"
          :key="item.id"
          :value="item.id"
          :label="item.name"
        />
      </el-select>
      <el-select
        v-model="directoryId"
        class="search__select"
        aria-label="按目录筛选"
        :disabled="!kbId"
      >
        <el-option value="" label="全部目录" />
        <el-option
          v-for="item in directories"
          :key="item.value"
          :value="item.value"
          :label="item.label"
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

    <p v-if="!session.loggedIn" class="search__hint">登录后可查看知识库/目录筛选。</p>

    <div v-if="loading" class="search__state">
      <div v-for="i in 3" :key="i" class="search__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="search__state">
      <p class="search__state-text">搜索失败</p>
      <el-button type="primary" plain @click="load(pageNo)">重试</el-button>
    </div>
    <div v-else-if="results.length === 0" class="search__state">
      <p class="search__state-text">没有找到相关知识，试试清空筛选或更换关键词。</p>
      <RouterLink class="search__reset" to="/search">清空筛选</RouterLink>
    </div>
    <template v-else>
      <p class="search__summary">共 {{ total }} 篇相关知识</p>
      <article v-for="knowledge in results" :key="knowledge.id" class="search-card">
        <RouterLink class="search-card__title" :to="`/knowledge/${knowledge.id}`">
          <span v-html="highlight(knowledge.title, keyword)" />
        </RouterLink>
        <p class="search-card__summary" v-html="highlight(knowledge.summary, keyword)" />
        <div class="search-card__meta">
          <span v-if="knowledge.kbName" class="search-card__kb">{{ knowledge.kbName }}</span>
          <span>{{ formatDate(knowledge.publishedAt) }}</span>
          <span>{{ knowledge.readMinutes }} 分钟阅读</span>
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

.search__hint {
  margin-top: calc(-1 * var(--xl-space-2));
  margin-bottom: var(--xl-space-4);
  color: var(--xl-text-muted);
  font-size: 12px;
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
  align-items: center;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.search-card__kb {
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

:deep(mark) {
  padding: 0 2px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--xl-color-primary) 18%, transparent);
  color: inherit;
}
</style>
