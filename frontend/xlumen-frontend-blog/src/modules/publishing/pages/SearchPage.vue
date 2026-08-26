<script setup lang="ts">
// 搜索/标签页（B03）：关键词 + 标签 + 知识库/目录组合筛选，滚动触底自动追加，命中高亮。
// 三态检索由路由 query 的 mode 驱动（缺省 keyword）：
// keyword——MySQL 关键词列表（既有行为全部保留）；semantic——向量语义检索（仅登录，单次请求不过滤/不滚动）；
// ask——问小光自然语言问答（仅登录，SSE 流式回答 + 引用溯源），保持同页不跳转。
// 关键状态：搜索中骨架、无结果空态（清空筛选建议）、失败可重试；搜索结果仅含公开知识。
// 知识库/目录筛选需登录（fetchKnowledgeBases/fetchDirectoryTree 为鉴权接口，未登录显示空态说明）。
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { fetchKnowledges, fetchTags } from '@/modules/publishing/api/public'
import { useInfinitePage } from '@/composables/useInfinitePage'
import { useSessionStore } from '@/stores/session'
import { streamChat } from '@/modules/chat/api/chat'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import CitationCard from '@/modules/chat/components/CitationCard.vue'
import { activeTools, doneTools } from '@/modules/chat/utils/toolPanel'

import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import type { CategoryCount, KnowledgeCard } from '@/modules/publishing/api/public'
import type { Citation, ToolEvent } from '@/modules/chat/api/chat'

const PAGE_SIZE = 10
const SEMANTIC_PAGE_SIZE = 50

type SearchMode = 'keyword' | 'semantic' | 'ask'

function normalizeMode(value: unknown): SearchMode {
  return value === 'semantic' || value === 'ask' ? value : 'keyword'
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const mode = ref<SearchMode>(normalizeMode(route.query.mode))
const keyword = ref((route.query.keyword as string | undefined) ?? '')
const kbId = ref((route.query.kbId as string | undefined) ?? '')
const directoryId = ref((route.query.directoryId as string | undefined) ?? '')
const tag = ref((route.query.tag as string | undefined) ?? '')
const knowledgeBases = ref<KnowledgeBase[]>([])
const directories = ref<DirectoryOption[]>([])
const tags = ref<CategoryCount[]>([])
const supportLoaded = ref(false)

const sentinel = ref<HTMLElement | null>(null)

interface DirectoryOption {
  value: string
  label: string
}

// 语义检索（单次请求，后端单页最多 50 条；不参与无限滚动）。semanticSearched 区分「未搜索」与「无结果」。
const semanticItems = ref<KnowledgeCard[]>([])
const semanticTotal = ref(0)
const semanticLoading = ref(false)
const semanticError = ref(false)
const semanticSearched = ref(false)
let semanticGeneration = 0

// 问小光（单题流式问答，不保留会话历史）
const question = ref('')
const asking = ref(false)
const answer = reactive({
  content: '',
  citations: [] as Citation[],
  tools: [] as ToolEvent[],
  streaming: false,
})
let askController: AbortController | null = null

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

/** 筛选下拉支撑数据（标签/知识库），keyword 模式首次进入时加载一次。 */
function loadSupportData(): Promise<void> {
  if (supportLoaded.value) return Promise.resolve()
  supportLoaded.value = true
  const tasks: Promise<unknown>[] = [fetchTags().then((list) => (tags.value = list))]
  if (session.loggedIn) tasks.push(loadKnowledgeBases())
  return Promise.all(tasks).then(() => undefined)
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

/** 提交搜索（keyword/semantic 共用输入框）：keyword 写 URL 驱动；semantic 直接执行单次查询。 */
function onSearchSubmit(): void {
  if (mode.value === 'keyword') {
    applyKeywordFilters()
  } else {
    void runSemanticSearch()
  }
}

/** keyword 模式提交筛选：更新 URL 查询参数（组合筛选的单一事实源）。 */
function applyKeywordFilters(): void {
  const query: Record<string, string> = {}
  const k = keyword.value.trim()
  if (k) query.keyword = k
  if (tag.value) query.tag = tag.value
  if (kbId.value) query.kbId = kbId.value
  if (kbId.value && directoryId.value) query.directoryId = directoryId.value
  void router.push({ name: 'search', query })
}

/** 语义检索单次请求；generation 防旧响应覆盖新结果。 */
async function runSemanticSearch(): Promise<void> {
  const current = ++semanticGeneration
  semanticLoading.value = true
  semanticError.value = false
  const q = keyword.value.trim()
  if (!q) {
    semanticItems.value = []
    semanticTotal.value = 0
    semanticSearched.value = false
    semanticLoading.value = false
    return
  }
  try {
    const page = await fetchKnowledges({
      keyword: q,
      mode: 'semantic',
      pageNo: 1,
      pageSize: SEMANTIC_PAGE_SIZE,
    })
    if (current !== semanticGeneration) return
    semanticItems.value = page.records
    semanticTotal.value = page.total
    semanticSearched.value = true
  } catch {
    if (current === semanticGeneration) semanticError.value = true
  } finally {
    if (current === semanticGeneration) semanticLoading.value = false
  }
}

/** 模式切换（radio 变更）：keyword 缺省省略 mode；semantic 顺带携带当前关键词便于直达。 */
function onModeChange(next: string | number | boolean | undefined): void {
  const target = normalizeMode(next)
  if (target === mode.value) return
  if (target !== 'keyword' && !session.loggedIn) return
  const query: Record<string, string> = {}
  if (target !== 'keyword') query.mode = target
  if (target === 'keyword') {
    const k = keyword.value.trim()
    if (k) query.keyword = k
    if (tag.value) query.tag = tag.value
    if (kbId.value) query.kbId = kbId.value
    if (kbId.value && directoryId.value) query.directoryId = directoryId.value
  } else if (target === 'semantic') {
    const k = keyword.value.trim()
    if (k) query.keyword = k
  }
  void router.push({ name: 'search', query })
}

/** 问小光：提交问题并流式接收回答；conversationId 不传=每次都是新的一问一答。 */
async function askQuestion(): Promise<void> {
  const q = question.value.trim()
  if (!q || asking.value) return
  resetAnswer()
  asking.value = true
  const controller = new AbortController()
  askController = controller
  try {
    await streamChat(
      { query: q },
      {
        onChunk: (text) => {
          answer.content += text
        },
        onTool: (event) => {
          answer.tools.push(event)
        },
        onCitations: (citations) => {
          answer.citations = citations
        },
        onDone: () => undefined,
      },
      controller.signal,
    )
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError') && !answer.content) {
      answer.content = error instanceof Error ? error.message : '回答失败，请稍后重试'
    }
  } finally {
    answer.streaming = false
    asking.value = false
    askController = null
  }
}

function resetAnswer(): void {
  answer.content = ''
  answer.citations = []
  answer.tools = []
  answer.streaming = false
}

/** 清空/重新提问：终止进行中的流并复位问答区。 */
function resetAsk(): void {
  askController?.abort()
  question.value = ''
  resetAnswer()
}

const infinite = useInfinitePage<KnowledgeCard>({
  sentinel,
  pageSize: PAGE_SIZE,
  loadPage: (pageNo, pageSize) =>
    fetchKnowledges({
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(kbId.value ? { kbId: kbId.value } : {}),
      ...(kbId.value && directoryId.value ? { directoryId: directoryId.value } : {}),
      ...(tag.value ? { tag: tag.value } : {}),
      pageNo,
      pageSize,
    }),
})

/** 模式分流后的统一列表状态（keyword=无限分页；semantic=single-shot 列表）。 */
const results = computed(() =>
  mode.value === 'semantic' ? semanticItems.value : infinite.items.value,
)
const listLoading = computed(() =>
  mode.value === 'semantic' ? semanticLoading.value : infinite.loading.value,
)
const listError = computed(() =>
  mode.value === 'semantic' ? semanticError.value : infinite.error.value,
)
const listTotal = computed(() =>
  mode.value === 'semantic' ? semanticTotal.value : infinite.total.value,
)
const modeLocked = computed(() => mode.value !== 'keyword' && !session.loggedIn)

function retryList(): void {
  if (mode.value === 'semantic') {
    void runSemanticSearch()
  } else {
    void infinite.retry()
  }
}

function formatScore(score?: number | null): string {
  return score == null ? '—' : score.toFixed(2)
}

/** 卡片跳转：语义结果带首个命中段落锚点直达原文；其余仅进详情页。 */
function cardHref(knowledge: KnowledgeCard): string {
  const base = `/knowledge/${knowledge.id}`
  return mode.value === 'semantic' && knowledge.firstAnchor
    ? `${base}#${knowledge.firstAnchor}`
    : base
}

// 从 URL 恢复筛选状态并查询（顶栏搜索框与标签链接共用本页）；directoryId 仅在 kbId 存在时生效
watch(
  () => route.query,
  (query) => {
    const nextMode = normalizeMode(query.mode)
    mode.value = nextMode
    keyword.value = (query.keyword as string | undefined) ?? ''
    tag.value = (query.tag as string | undefined) ?? ''
    kbId.value = (query.kbId as string | undefined) ?? ''
    directoryId.value = kbId.value ? ((query.directoryId as string | undefined) ?? '') : ''
    if (mode.value === 'keyword') {
      if (!supportLoaded.value) void loadSupportData()
      void infinite.loadFirst()
    } else if (mode.value === 'semantic' && session.loggedIn) {
      void runSemanticSearch()
    }
    // ask 模式不自动发起请求：问题仅在用户提交时流式作答
  },
)

onMounted(() => {
  if (mode.value === 'keyword') {
    void infinite.loadFirst()
    void loadSupportData()
  } else if (mode.value === 'semantic' && session.loggedIn) {
    void runSemanticSearch()
  }
})
</script>

<template>
  <main class="search">
    <div class="search__modes" aria-label="检索模式">
      <el-radio-group :model-value="mode" @change="onModeChange">
        <el-radio value="keyword">关键词</el-radio>
        <el-tooltip content="登录后可用语义检索（向量相似度）" :disabled="session.loggedIn">
          <el-radio value="semantic" :disabled="!session.loggedIn">向量语义</el-radio>
        </el-tooltip>
        <el-tooltip content="登录后可用「问小光」自然语言问答" :disabled="session.loggedIn">
          <el-radio value="ask" :disabled="!session.loggedIn">问小光</el-radio>
        </el-tooltip>
      </el-radio-group>
    </div>

    <div v-if="modeLocked" class="search__state">
      <p class="search__state-text">
        {{
          mode === 'ask'
            ? '「问小光」需要登录后使用：基于本站知识作答，回答附带引用溯源。'
            : '向量语义检索需要登录后使用：基于向量相似度匹配相关知识。'
        }}
      </p>
      <RouterLink class="search__reset" to="/login?redirect=/search">去登录</RouterLink>
    </div>

    <template v-else-if="mode === 'ask'">
      <form class="search__form" @submit.prevent="askQuestion">
        <el-input
          v-model="question"
          class="search__input"
          placeholder="输入你的问题…"
          aria-label="问题内容"
          clearable
        />
        <el-button
          type="primary"
          native-type="submit"
          class="search__submit"
          :disabled="asking || !question.trim()"
        >
          {{ asking ? '回答中…' : '提问' }}
        </el-button>
      </form>

      <div v-if="answer.content || answer.citations.length > 0 || asking" class="ask">
        <div class="ask__answer markdown-body" v-html="renderMarkdown(answer.content)"></div>
        <span v-if="asking" class="ask__cursor" aria-hidden="true">▍</span>
        <div v-if="activeTools(answer.tools).length > 0" class="ask__tool-line">
          正在检索知识库…
        </div>
        <p v-if="doneTools(answer.tools).length > 0" class="ask__tools">
          共调用 {{ doneTools(answer.tools).length }} 个工具
        </p>
        <div v-if="answer.citations.length > 0" class="ask__citations">
          <CitationCard
            v-for="(citation, index) in answer.citations"
            :key="`${citation.knowledgeId}-${citation.chunkSeq}-${index}`"
            :citation="citation"
            :index="index + 1"
          />
        </div>
        <el-button
          v-if="!asking && (answer.content || answer.citations.length > 0)"
          type="primary"
          plain
          size="small"
          class="ask__reset"
          @click="resetAsk"
        >
          重新提问
        </el-button>
      </div>
      <p v-else class="search__hint">输入问题，「小光」将基于本站知识作答并附带可溯源的引用。</p>
    </template>

    <template v-else>
      <form class="search__form" @submit.prevent="onSearchSubmit">
        <el-input
          v-model="keyword"
          class="search__input"
          :placeholder="
            mode === 'semantic' ? '输入关键词，按向量相似度检索…' : '搜索知识标题或摘要…'
          "
          aria-label="搜索关键词"
          clearable
        />
        <template v-if="mode === 'keyword'">
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
        </template>
        <el-button type="primary" native-type="submit" class="search__submit">搜索</el-button>
      </form>

      <p v-if="mode === 'keyword' && !session.loggedIn" class="search__hint">
        登录后可查看知识库/目录筛选。
      </p>

      <div v-if="listLoading" class="search__state">
        <div v-for="i in 3" :key="i" class="search__skeleton" aria-hidden="true" />
      </div>
      <div v-else-if="listError" class="search__state">
        <p class="search__state-text">搜索失败</p>
        <el-button type="primary" plain @click="retryList()">重试</el-button>
      </div>
      <div v-else-if="results.length === 0" class="search__state">
        <p class="search__state-text">
          <template v-if="mode === 'semantic' && !semanticSearched"
            >输入关键词开始向量语义检索。</template
          >
          <template v-else-if="mode === 'semantic'"
            >没有找到语义相关知识，试试更换关键词。</template
          >
          <template v-else>没有找到相关知识，试试清空筛选或更换关键词。</template>
        </p>
        <RouterLink
          v-if="mode !== 'semantic' || semanticSearched"
          class="search__reset"
          to="/search"
        >
          清空筛选
        </RouterLink>
      </div>
      <template v-else>
        <p class="search__summary">
          {{
            mode === 'semantic' ? `找到 ${listTotal} 篇语义相关知识` : `共 ${listTotal} 篇相关知识`
          }}
        </p>
        <div class="search__cards">
          <article v-for="knowledge in results" :key="knowledge.id" class="search-card">
            <RouterLink class="search-card__title" :to="cardHref(knowledge)">
              <span v-html="highlight(knowledge.title, keyword)" />
            </RouterLink>
            <p class="search-card__summary" v-html="highlight(knowledge.summary, keyword)" />
            <div class="search-card__meta">
              <span v-if="knowledge.kbName" class="search-card__kb">{{ knowledge.kbName }}</span>
              <span v-if="mode === 'semantic'" class="search-card__badge">
                相关度 {{ formatScore(knowledge.semanticScore) }}
              </span>
              <span
                v-if="mode === 'semantic' && knowledge.chunkCount != null"
                class="search-card__badge"
              >
                命中 {{ knowledge.chunkCount }} 段
              </span>
              <span>{{ formatDate(knowledge.publishedAt) }}</span>
              <span>{{ knowledge.readMinutes }} 分钟阅读</span>
            </div>
          </article>
        </div>
        <template v-if="mode === 'keyword'">
          <div ref="sentinel" class="search__sentinel" aria-hidden="true" />
          <div v-if="infinite.loadingMore" class="search__load-more" role="status">加载更多…</div>
          <div v-else-if="infinite.loadMoreError" class="search__load-more">
            <el-button type="primary" plain size="small" @click="infinite.retryMore()"
              >重试加载</el-button
            >
          </div>
          <div v-else-if="!infinite.hasMore" class="search__load-more">已加载全部知识</div>
        </template>
      </template>
    </template>
  </main>
</template>

<style scoped>
.search {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.search__modes {
  margin-bottom: var(--xl-space-4);
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

.search__cards {
  column-count: 2;
  column-gap: var(--xl-space-4);
}

.search__sentinel {
  height: 1px;
}

.search__load-more {
  min-height: 34px;
  padding: 14px 0 4px;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: center;
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
  break-inside: avoid;
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

@media (width <= 640px) {
  .search__cards {
    column-count: 1;
  }
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

.search-card__badge {
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-ai) 10%, transparent);
  color: var(--xl-color-ai);
}

:deep(mark) {
  padding: 0 2px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--xl-color-primary) 18%, transparent);
  color: inherit;
}

.ask {
  padding: var(--xl-space-4) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-left: 3px solid var(--xl-color-ai);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.ask__answer {
  color: var(--xl-text-primary);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: break-word;
}

.ask__cursor {
  color: var(--xl-color-ai);
}

.ask__tool-line {
  margin-top: 6px;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.ask__tool-line::before {
  content: '● ';
  color: var(--xl-color-ai);
}

.ask__tools {
  margin-top: 6px;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.ask__citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.ask__reset {
  margin-top: var(--xl-space-3);
}

/* Markdown 回答体：块级标签自带分段，白底容器内直接铺排 */
.markdown-body :deep(p) {
  margin: 0.6em 0;
}

.markdown-body :deep(p:first-child),
.markdown-body :deep(p:last-child) {
  margin-block: 0;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.1em 0 0.5em;
}

.markdown-body :deep(a) {
  color: var(--xl-color-primary);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.6em;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--xl-border) 70%, transparent);
  font-family: var(--xl-font-mono);
  font-size: 13px;
}

.markdown-body :deep(pre) {
  margin: 0.6em 0;
  padding: var(--xl-space-3);
  overflow-x: auto;
  border-radius: 8px;
  background: var(--xl-text-primary);
  color: #fff;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  margin: 0.6em 0;
  padding: 0 var(--xl-space-3);
  border-left: 3px solid var(--xl-color-primary);
  color: var(--xl-text-secondary);
}
</style>
