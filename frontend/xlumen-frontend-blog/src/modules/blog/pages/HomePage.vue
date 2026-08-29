<script setup lang="ts">
// 首页（B01，KB-4 知识平台化）：AI 光带入口（品牌记忆点）+ 左侧吸顶「探索」轨 + 右侧标签知识流。
// 功能真值：AI 光带只做路由跳转（问小光→/search?mode=ask、AI 写作→/studio/writing、对比文档→/chat、知识地图→/map），
// 不在主页模拟生成/对比/会话恢复/Agent 执行；知识流只展示已有字段，不添加缩略图等不存在的字段。
// 目录树/标签云/库切换仅在登录态可用（后端 /knowledge-bases 为鉴权接口），未登录首页为纯列表流。
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  ChatDotRound,
  Collection,
  Grid,
  MapLocation,
  Promotion,
  EditPen,
} from '@element-plus/icons-vue'

import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { fetchKnowledges, fetchTags } from '@/modules/publishing/api/public'
import DirectoryTreeContextMenu from '@/modules/knowledge/components/DirectoryTreeContextMenu.vue'
import { useInfinitePage } from '@/composables/useInfinitePage'
import { useSessionStore } from '@/stores/session'
import SegmentedControl from '@/components/SegmentedControl.vue'
import InitialAvatar from '@/components/InitialAvatar.vue'

import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import type { CategoryCount, KnowledgeCard } from '@/modules/publishing/api/public'

const PAGE_SIZE = 10

const session = useSessionStore()
const router = useRouter()

// ---- AI 光带入口（仅路由跳转） ----
const homeKeyword = ref('')
const homeMode = ref('keyword')
const glowModes = [
  { label: '关键词', value: 'keyword' },
  { label: '向量语义', value: 'semantic' },
  { label: '问小光', value: 'ask' },
]
const glowEntries = [
  { icon: ChatDotRound, title: 'AI小光对话', desc: '与知识对话，获得洞察', to: '/chat', ai: true },
  { icon: EditPen, title: 'AI 写作', desc: '基于知识，辅助创作', to: '/studio/writing', ai: false },
  { icon: Grid, title: '对比文档', desc: '多文档对照与洞察', to: '/chat', ai: false },
  { icon: MapLocation, title: '知识地图', desc: '可视化探索知识全景', to: '/map', ai: false },
]

function onGlowMode(value: string): void {
  homeMode.value = value
  void router.push({ name: 'search', query: value === 'keyword' ? {} : { mode: value } })
}

function submitHomeSearch(): void {
  const q = homeKeyword.value.trim()
  void router.push({ name: 'search', query: q ? { keyword: q } : {} })
  homeKeyword.value = ''
}

// ---- 右栏列表状态 ----
const sentinel = ref<HTMLElement | null>(null)

// ---- B01-C 滚动态：AI 光带离场后顶部浅提示「上滑唤回 AI 快速操作」 ----
const glowBandEl = ref<HTMLElement | null>(null)
const showReturnTip = ref(false)

function onGlowScroll(): void {
  const el = glowBandEl.value
  if (!el) return
  showReturnTip.value = el.getBoundingClientRect().bottom <= 64
}

function scrollToTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ---- 左栏「探索」轨状态（空 kbId = 全部知识库） ----
const myKnowledgeBases = ref<KnowledgeBase[]>([])
const selectedKbId = ref('')
const selectedDirectoryId = ref('')
const selectedDirectoryName = ref('')
const selectedTag = ref('')
const directoryTree = ref<DirectoryNode[]>([])
const tags = ref<CategoryCount[]>([])
const sideLoading = ref(false)

const publicKnowledgeBases = computed(() =>
  myKnowledgeBases.value.filter((kb) => kb.visibility === 1),
)
const privateKbIds = computed(
  () => new Set(myKnowledgeBases.value.filter((kb) => kb.visibility === 0).map((kb) => kb.id)),
)
const selectedKb = computed(() => myKnowledgeBases.value.find((kb) => kb.id === selectedKbId.value))

// 库主判定：左栏库切换器数据源为 fetchKnowledgeBases（鉴权接口，仅返回登录用户自己的库），
// 选中库必然属于当前用户，故「已选中某库」即等价于库主，右键菜单可用。
const isKbOwner = computed(() => Boolean(selectedKb.value))

/** 右键菜单实例（open(event, node?) 由目录树 contextmenu 调用，node 省略 = 树根）。 */
const dirMenu = ref<InstanceType<typeof DirectoryTreeContextMenu> | null>(null)

/** 范围标题：全部知识库 / [库名] / [目录名]。 */
const scopeTitle = computed(() => {
  if (!selectedKbId.value) return '全部知识库'
  const kbName = selectedKb.value?.name ?? ''
  return selectedDirectoryName.value ? `${kbName} / ${selectedDirectoryName.value}` : kbName
})

/** 空态文案：区分全站无公开知识 / 当前范围无知识 / 筛选无结果。 */
const emptyText = computed(() => {
  if (selectedTag.value) return '没有筛选到相关知识，换个标签试试。'
  if (selectedKbId.value) return '当前范围还没有知识。'
  return '还没有公开知识，敬请期待。'
})

/** 目录树扁平化（多级缩进渲染）。 */
interface FlatDirectory {
  id: string
  name: string
  knowledgeCount: number
  depth: number
}

function flattenTree(nodes: DirectoryNode[], depth = 0): FlatDirectory[] {
  return nodes.flatMap((node) => [
    { id: node.id, name: node.name, knowledgeCount: node.knowledgeCount, depth },
    ...flattenTree(node.children ?? [], depth + 1),
  ])
}

function formatDate(iso: string): string {
  return iso.slice(0, 10)
}

/** 卡片 🔒 标记：kbId 属于登录用户自己的私有库（后端不返回私有标记，前端比对）。 */
function isPrivateCard(knowledge: KnowledgeCard): boolean {
  return session.loggedIn && Boolean(knowledge.kbId) && privateKbIds.value.has(knowledge.kbId)
}

const infinite = useInfinitePage<KnowledgeCard>({
  sentinel,
  pageSize: PAGE_SIZE,
  loadPage: (pageNo, pageSize) =>
    fetchKnowledges({
      ...(selectedKbId.value ? { kbId: selectedKbId.value } : {}),
      ...(selectedDirectoryId.value ? { directoryId: selectedDirectoryId.value } : {}),
      ...(selectedTag.value ? { tag: selectedTag.value } : {}),
      pageNo,
      pageSize,
    }),
})

const knowledges = infinite.items
const total = infinite.total
const loading = infinite.loading
const loadError = infinite.error
// 嵌套在普通对象里的 ref 模板不自动解包，须先提升为顶层 ref 再用于 v-if
const loadingMore = infinite.loadingMore
const loadMoreError = infinite.loadMoreError
const hasMore = infinite.hasMore

/** 库切换器：command 为 'all' 表示全部知识库，否则为库 ID。 */
async function switchKb(command: string): Promise<void> {
  selectedKbId.value = command === 'all' ? '' : command
  selectedDirectoryId.value = ''
  selectedDirectoryName.value = ''
  selectedTag.value = ''
  directoryTree.value = []
  if (selectedKbId.value) {
    sideLoading.value = true
    directoryTree.value = await fetchDirectoryTree(selectedKbId.value).catch(() => [])
    sideLoading.value = false
  }
  await infinite.loadFirst()
}

/** 选中/取消目录（再次点击取消）；选中后列表按创建时间正序（后端保证）。 */
async function toggleDirectory(node: FlatDirectory): Promise<void> {
  if (selectedDirectoryId.value === node.id) {
    selectedDirectoryId.value = ''
    selectedDirectoryName.value = ''
  } else {
    selectedDirectoryId.value = node.id
    selectedDirectoryName.value = node.name
  }
  await infinite.loadFirst()
}

/** 标签筛选（再次点击取消）。 */
async function toggleTag(name: string): Promise<void> {
  selectedTag.value = selectedTag.value === name ? '' : name
  await infinite.loadFirst()
}

/** 右键菜单操作成功后刷新目录树（知识数随树节点返回；失败保留原树）。 */
async function refreshDirectories(): Promise<void> {
  if (!selectedKbId.value) return
  directoryTree.value = await fetchDirectoryTree(selectedKbId.value).catch(
    () => directoryTree.value,
  )
}

/** 删除目录后：选中目录在删除范围内则重置为「全部知识」，并重新拉取列表（知识上挂父目录）。 */
function onDirectoryDeleted(ids: string[]): void {
  if (selectedDirectoryId.value && ids.includes(selectedDirectoryId.value)) {
    selectedDirectoryId.value = ''
    selectedDirectoryName.value = ''
  }
  void infinite.loadFirst()
}

function openKnowledge(id: string): void {
  void router.push(`/knowledge/${id}`)
}

onMounted(async () => {
  if (session.loggedIn) {
    const [kbs, tagList] = await Promise.all([
      fetchKnowledgeBases().catch(() => []),
      fetchTags().catch(() => []),
    ])
    myKnowledgeBases.value = kbs
    tags.value = tagList
  }
  await infinite.loadFirst()
  window.addEventListener('scroll', onGlowScroll, { passive: true })
  onGlowScroll()
})

onUnmounted(() => window.removeEventListener('scroll', onGlowScroll))
</script>

<template>
  <main class="home">
    <!-- AI 光带入口：只做路由跳转，不模拟任何生成/对比/会话 -->
    <section ref="glowBandEl" class="home__glow">
      <div class="home__glow-left">
        <h1 class="home__glow-title">让知识会回答，也会继续生长</h1>
        <p class="home__glow-sub">汇聚知识、连接知识、创造知识。</p>
        <SegmentedControl
          :model-value="homeMode"
          :options="glowModes"
          class="home__glow-modes"
          @update:model-value="onGlowMode"
        />
        <form class="home__glow-search" @submit.prevent="submitHomeSearch">
          <el-input
            v-model="homeKeyword"
            class="home__glow-search-input"
            type="search"
            placeholder="搜索知识、文档、主题或问题…"
            aria-label="搜索知识"
          />
          <button type="submit" class="home__glow-submit" aria-label="搜索">
            <el-icon><Promotion /></el-icon>
          </button>
        </form>
      </div>
      <div class="home__glow-right">
        <RouterLink
          v-for="entry in glowEntries"
          :key="entry.title"
          class="glow-entry"
          :to="entry.to"
        >
          <span class="glow-entry__icon" :class="{ 'glow-entry__icon--ai': entry.ai }">
            <el-icon><component :is="entry.icon" /></el-icon>
            <span v-if="entry.ai" class="glow-entry__ai" aria-hidden="true">✦</span>
          </span>
          <span class="glow-entry__body">
            <span class="glow-entry__title">{{ entry.title }}</span>
            <span class="glow-entry__desc">{{ entry.desc }}</span>
          </span>
          <el-icon v-if="entry.title === '知识地图'" class="glow-entry__arrow"
            ><ArrowRight
          /></el-icon>
        </RouterLink>
      </div>
    </section>

    <!-- B01-C 滚动态：AI 光带离场后顶部轻提示「上滑唤回 AI 快速操作」 -->
    <transition name="glow-tip">
      <button v-if="showReturnTip" type="button" class="home__return" @click="scrollToTop">
        <el-icon><ArrowUp /></el-icon>
        上滑唤回 AI 快速操作
      </button>
    </transition>

    <!-- 知识流：左探索轨 + 右侧知识列表 -->
    <div class="home__layout">
      <aside class="home__side">
        <h2 class="home__side-title">探索</h2>

        <!-- 库切换器（登录态）：全部知识库 + 我的知识库 -->
        <el-dropdown v-if="session.loggedIn" trigger="click" @command="switchKb">
          <button type="button" class="home__switcher">
            <span class="home__switcher-icon"
              ><el-icon><Collection /></el-icon
            ></span>
            <span class="home__switcher-label">{{ scopeTitle }}</span>
            <el-icon class="home__dropdown-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="all" :disabled="selectedKbId === ''"
                >全部知识库</el-dropdown-item
              >
              <el-dropdown-item
                v-for="kb in myKnowledgeBases"
                :key="kb.id"
                :command="kb.id"
                :disabled="selectedKbId === kb.id"
              >
                {{ kb.name }}<span v-if="kb.visibility === 0" class="home__kb-lock"> 🔒</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 未登录：左栏说明 -->
        <section v-else class="side-card home__guest-hint">
<!--          <p class="home__guest-text">登录探索更多</p>-->
          <RouterLink class="home__guest-login" to="/login">登录探索更多</RouterLink>
        </section>

        <template v-if="session.loggedIn">
          <!-- 选中库：当前库目录树 -->
          <template v-if="selectedKbId">
            <section class="side-card" @contextmenu="dirMenu?.open($event)">
              <h3 class="side-card__title">目录</h3>
              <p v-if="sideLoading" class="side-card__hint">目录加载中…</p>
              <p v-else-if="directoryTree.length === 0" class="side-card__hint">该知识库暂无目录</p>
              <ul v-else class="home__tree">
                <li v-for="node in flattenTree(directoryTree)" :key="node.id">
                  <button
                    type="button"
                    class="home__dir"
                    :class="{ 'home__dir--active': node.id === selectedDirectoryId }"
                    :style="{ paddingLeft: `${12 + node.depth * 14}px` }"
                    @click="toggleDirectory(node)"
                    @contextmenu.stop="dirMenu?.open($event, { id: node.id, name: node.name })"
                  >
                    <span>{{ node.name }}</span>
                    <span class="home__dir-count">{{ node.knowledgeCount }}</span>
                  </button>
                </li>
              </ul>
            </section>
          </template>

          <!-- 全部知识库：我的公开库列表 + 我的知识库入口 -->
          <template v-else>
            <section class="side-card">
              <h3 class="side-card__title">我的公开库</h3>
              <p v-if="publicKnowledgeBases.length === 0" class="side-card__hint">暂无我的公开库</p>
              <div class="home__side-list">
                <RouterLink
                  v-for="kb in publicKnowledgeBases"
                  :key="kb.id"
                  class="home__side-link"
                  :to="`/kb/${kb.id}`"
                >
                  <span>{{ kb.name }}</span>
                  <span class="home__side-count">{{ kb.knowledgeCount }}</span>
                </RouterLink>
              </div>
            </section>
            <RouterLink class="home__mine" :to="{ name: 'kb-discovery', query: { mine: '1' } }">
              我的知识库 →
            </RouterLink>
          </template>

          <!-- 热门标签（登录态展示） -->
          <section v-if="tags.length > 0" class="side-card">
            <h3 class="side-card__title">热门标签</h3>
            <div class="home__tags">
              <button
                v-for="tag in tags"
                :key="tag.name"
                type="button"
                class="home__tag"
                :class="{ 'home__tag--active': tag.name === selectedTag }"
                @click="toggleTag(tag.name)"
              >
                {{ tag.name }}
              </button>
            </div>
          </section>
        </template>
      </aside>

      <section class="home__main">
        <header class="home__list-head">
          <h2 class="home__title">{{ scopeTitle }}</h2>
          <p class="home__meta">共 {{ total }} 篇</p>
        </header>

        <div v-if="loading" class="home__state">
          <div v-for="i in 4" :key="i" class="home__skeleton" aria-hidden="true" />
        </div>
        <div v-else-if="loadError" class="home__state">
          <p class="home__state-text">知识加载失败</p>
          <button type="button" class="home__retry" @click="infinite.retry()">重试</button>
        </div>
        <template v-else>
          <div v-if="knowledges.length === 0" class="home__empty">
            <el-icon class="home__empty-icon"><Collection /></el-icon>
            <p>{{ emptyText }}</p>
          </div>
          <div v-if="knowledges.length > 0" class="home__list">
            <article
              v-for="knowledge in knowledges"
              :key="knowledge.id"
              class="knowledge-row"
              @click="openKnowledge(knowledge.id)"
            >
              <div class="knowledge-row__body">
                <div class="knowledge-row__head">
                  <RouterLink
                    class="knowledge-row__title"
                    :to="`/knowledge/${knowledge.id}`"
                    @click.stop
                  >
                    {{ knowledge.title }}
                  </RouterLink>
                  <div class="knowledge-row__badges">
                    <RouterLink
                      v-if="knowledge.kbName"
                      class="knowledge-row__kb"
                      :to="`/kb/${knowledge.kbId}`"
                      @click.stop
                    >
                      <el-tag size="small" round effect="plain">{{ knowledge.kbName }}</el-tag>
                    </RouterLink>
                    <el-tag
                      v-if="isPrivateCard(knowledge)"
                      size="small"
                      round
                      effect="plain"
                      class="knowledge-row__private"
                    >
                      🔒 私有
                    </el-tag>
                  </div>
                </div>
                <p class="knowledge-row__summary">{{ knowledge.summary }}</p>
                <div class="knowledge-row__meta">
                  <InitialAvatar :name="knowledge.authorName" :size="22" />
                  <span class="knowledge-row__author">{{ knowledge.authorName }}</span>
                  <span>{{ formatDate(knowledge.publishedAt) }}</span>
                  <span>{{ knowledge.readMinutes }} 分钟阅读</span>
                  <span>{{ knowledge.viewCount }} 阅读</span>
                  <span v-if="knowledge.commentCount > 0">{{ knowledge.commentCount }} 评论</span>
                  <span v-if="knowledge.likeCount > 0">{{ knowledge.likeCount }} 点赞</span>
                </div>
                <div v-if="knowledge.tags.length > 0" class="knowledge-row__tags">
                  <RouterLink
                    v-for="tag in knowledge.tags"
                    :key="tag"
                    class="knowledge-row__tag"
                    :to="`/search?tag=${encodeURIComponent(tag)}`"
                    @click.stop
                  >
                    {{ tag }}
                  </RouterLink>
                </div>
              </div>
            </article>
          </div>
          <div ref="sentinel" class="home__sentinel" aria-hidden="true" />
          <div v-if="loadingMore" class="home__load-more" role="status">加载更多…</div>
          <div v-else-if="loadMoreError" class="home__load-more">
            <el-button type="primary" plain size="small" @click="infinite.retryMore()"
              >重试加载</el-button
            >
          </div>
          <div v-else-if="!hasMore" class="home__load-more">已加载全部知识</div>
        </template>
      </section>
    </div>

    <!-- 目录树右键菜单（新增/重命名/删除，仅库主） -->
    <DirectoryTreeContextMenu
      ref="dirMenu"
      :kb-id="selectedKbId"
      :owner="isKbOwner"
      :directories="directoryTree"
      @refresh="refreshDirectories"
      @deleted="onDirectoryDeleted"
    />
  </main>
</template>

<style scoped>
.home {
  width: 100%;
  box-sizing: border-box;
}

/* ===== AI 光带入口 ===== */
.home__glow {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: var(--xl-space-8);
  align-items: stretch;
  min-height: 292px;
  padding: var(--xl-space-6)
    max(var(--xl-content-pad), calc((100% - var(--xl-container)) / 2 + var(--xl-content-pad)));
  background:
    radial-gradient(
      68% 150% at 75% 48%,
      color-mix(in srgb, var(--xl-color-primary) 16%, transparent),
      transparent 62%
    ),
    var(--xl-bg-page);
  border-bottom: 1px solid var(--xl-border);
}

/* 参考图中的“知识光轨”：用低对比几何光束表达连接，不引入额外图片资产。 */
.home__glow::before {
  content: '';
  position: absolute;
  top: -22%;
  right: 28%;
  z-index: -1;
  width: 58%;
  height: 145%;
  border-radius: 50%;
  background:
    linear-gradient(76deg, transparent 47%, rgb(83 103 232 / 9%) 49%, transparent 51%),
    linear-gradient(84deg, transparent 58%, rgb(18 165 148 / 7%) 59%, transparent 60%),
    radial-gradient(ellipse at 80% 50%, rgb(255 255 255 / 68%), transparent 58%);
  opacity: 0.8;
  transform: rotate(-7deg);
  pointer-events: none;
}

.home__glow::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 25%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--xl-color-ai);
  box-shadow:
    0 0 0 6px color-mix(in srgb, var(--xl-color-ai) 9%, transparent),
    0 0 24px color-mix(in srgb, var(--xl-color-ai) 24%, transparent);
  opacity: 0.75;
  transform: translate(50%, -50%);
  pointer-events: none;
}

.home__glow-left,
.home__glow-right {
  position: relative;
  z-index: 1;
}

.home__glow-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 720px;
  gap: var(--xl-space-3);
}

.home__glow-title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.home__glow-sub {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.home__glow-modes {
  margin-top: var(--xl-space-2);
}

.home__glow-search {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  max-width: 680px;
  margin-top: var(--xl-space-2);
}

.home__glow-search-input {
  flex: 1;
  max-width: 640px;
}

.home__glow-search-input :deep(.el-input__wrapper) {
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: none;
  border: 1px solid var(--xl-border);
}

.home__glow-search-input :deep(.el-input__wrapper.is-focus),
.home__glow-search-input :deep(.el-input__wrapper:hover) {
  border-color: var(--xl-color-primary);
}

.home__glow-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  cursor: pointer;
  transition: background var(--xl-transition);
}

.home__glow-submit:hover {
  background: var(--xl-color-primary-hover);
}

/* B01-C 滚动态：导航下沿浅提示「上滑唤回 AI 快速操作」 */
.home__return {
  position: fixed;
  top: calc(var(--xl-header-h) + 8px);
  left: 50%;
  z-index: 90;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-md);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  cursor: pointer;
  transform: translateX(-50%);
  transition:
    color var(--xl-transition),
    border-color var(--xl-transition);
}

.home__return:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.glow-tip-enter-active,
.glow-tip-leave-active {
  transition:
    opacity var(--xl-transition),
    transform var(--xl-transition);
}

.glow-tip-enter-from,
.glow-tip-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}

.home__glow-right {
  position: relative;
  justify-self: end;
  width: min(100%, 560px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--xl-space-3);
  padding: var(--xl-space-4) var(--xl-space-3) var(--xl-space-4) var(--xl-space-6);
  border: 1px solid rgb(255 255 255 / 72%);
  border-radius: 20px;
  background: rgb(255 255 255 / 58%);
  box-shadow: 0 18px 44px rgb(83 103 232 / 8%);
  backdrop-filter: blur(12px);
}

.home__glow-right::before {
  content: '';
  position: absolute;
  top: 22px;
  bottom: 22px;
  left: 20px;
  width: 1px;
  background: linear-gradient(
    to bottom,
    transparent,
    color-mix(in srgb, var(--xl-color-primary) 32%, transparent) 18%,
    color-mix(in srgb, var(--xl-color-ai) 32%, transparent) 82%,
    transparent
  );
}

.glow-entry {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  padding: var(--xl-space-2) var(--xl-space-3);
  border-radius: var(--xl-radius);
  text-decoration: none;
  transition: background var(--xl-transition);
}

.glow-entry::before {
  content: '';
  position: absolute;
  left: -9px;
  width: 7px;
  height: 7px;
  border: 2px solid var(--xl-bg-page);
  border-radius: 50%;
  background: var(--xl-color-primary);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--xl-color-primary) 25%, transparent);
}

.glow-entry:first-child::before {
  background: var(--xl-color-ai);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--xl-color-ai) 28%, transparent);
}

.glow-entry:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 6%, transparent);
}

.glow-entry__icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--xl-radius);
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-size: 22px;
}

.glow-entry__icon--ai {
  background: color-mix(in srgb, var(--xl-color-ai) 12%, transparent);
  color: var(--xl-color-ai);
}

.glow-entry__ai {
  position: absolute;
  top: -6px;
  right: -6px;
  color: var(--xl-color-ai);
  font-size: 14px;
}

.glow-entry__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.glow-entry__title {
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
  font-weight: var(--xl-fs-title-w);
}

.glow-entry__desc {
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.glow-entry__arrow {
  margin-left: auto;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-body);
  transform: rotate(90deg);
}

/* ===== 知识流布局 ===== */
.home__layout {
  display: flex;
  gap: var(--xl-space-6);
  align-items: flex-start;
  width: min(100% - 48px, var(--xl-container));
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

.home__side {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
  position: sticky;
  top: calc(var(--xl-header-h) + var(--xl-space-6));
}

.home__side-title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

.home__main {
  flex: 1;
  min-width: 0;
  padding: var(--xl-space-4) var(--xl-space-6) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: 20px;
  background: var(--xl-bg-surface);
  box-shadow: 0 10px 30px rgb(22 32 51 / 3%);
}

/* 库切换器 */
.home__switcher {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
  font-weight: var(--xl-fs-title-w);
  cursor: pointer;
  transition: border-color var(--xl-transition);
}

.home__switcher:hover {
  border-color: var(--xl-color-primary);
}

.home__switcher-icon {
  color: var(--xl-color-primary);
  font-size: 16px;
}

.home__switcher-label {
  flex: 1;
  text-align: left;
}

.home__dropdown-arrow {
  color: var(--xl-text-muted);
  font-size: 14px;
}

/* 列表头部 */
.home__list-head {
  display: flex;
  align-items: baseline;
  gap: var(--xl-space-3);
  margin-bottom: var(--xl-space-3);
  padding-bottom: var(--xl-space-3);
  border-bottom: 1px solid var(--xl-border);
}

.home__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

.home__meta {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

/* 未登录提示 */
.home__guest-hint {
  text-align: center;
}

.home__guest-hint::before {
  content: '✦';
  display: block;
  margin-bottom: var(--xl-space-2);
  color: var(--xl-color-ai);
  font-size: 20px;
}

.home__guest-text {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.7;
}

.home__guest-login {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-caption);
  text-decoration: none;
}

/* 状态区 */
.home__state {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.home__skeleton {
  height: 96px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.home__state-text {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.home__retry {
  align-self: flex-start;
  padding: 6px 16px;
  border: none;
  border-radius: var(--xl-radius);
  background: var(--xl-color-primary);
  color: #fff;
  font-size: var(--xl-fs-caption);
  cursor: pointer;
}

.home__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--xl-space-3);
  padding: var(--xl-space-8) 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.home__empty-icon {
  font-size: 42px;
  color: var(--xl-text-muted);
}

.home__empty p {
  margin: 0;
}

/* 细分隔线知识流 */
.home__list {
  display: flex;
  flex-direction: column;
}

.knowledge-row {
  display: flex;
  align-items: flex-start;
  gap: var(--xl-space-4);
  padding: 18px 12px;
  margin: 0 -12px;
  border-bottom: 1px solid var(--xl-border);
  border-radius: 12px;
  cursor: pointer;
  transition: background var(--xl-transition);
}

.knowledge-row:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 4%, var(--xl-bg-surface));
}

.knowledge-row:last-child {
  border-bottom: none;
}

.knowledge-row__body {
  flex: 1;
  min-width: 0;
}

.knowledge-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-3);
}

.knowledge-row__title {
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  text-decoration: none;
  transition: color var(--xl-transition);
}

.knowledge-row__title:hover {
  color: var(--xl-color-primary);
}

.knowledge-row__badges {
  display: flex;
  flex-shrink: 0;
  gap: var(--xl-space-2);
}

.knowledge-row__kb {
  text-decoration: none;
}

.knowledge-row__private :deep(.el-tag__content) {
  color: var(--xl-color-warning);
}

.knowledge-row__summary {
  margin: var(--xl-space-2) 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.65;
}

.knowledge-row__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.knowledge-row__author {
  color: var(--xl-text-secondary);
  font-weight: 500;
}

.knowledge-row__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-3);
}

.knowledge-row__tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-caption);
  text-decoration: none;
}

.knowledge-row__tag:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 16%, transparent);
}

/* 左栏卡片 */
.side-card {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.side-card__title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
  font-weight: var(--xl-fs-title-w);
}

.side-card__hint {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.home__tree {
  list-style: none;
  margin: 0;
  padding: 0;
}

.home__dir {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-top: 5px;
  padding-bottom: 5px;
  padding-right: 8px;
  border: none;
  border-radius: var(--xl-radius-sm);
  background: none;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  text-align: left;
  cursor: pointer;
}

.home__dir:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.home__dir--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-weight: 600;
}

.home__dir-count {
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.home__side-list {
  display: flex;
  flex-direction: column;
}

.home__side-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: var(--xl-radius-sm);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  text-decoration: none;
}

.home__side-link:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.home__side-count {
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.home__mine {
  display: block;
  padding: 8px 10px;
  border: 1px dashed var(--xl-border);
  border-radius: var(--xl-radius-card);
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-caption);
  text-align: center;
  text-decoration: none;
}

.home__mine:hover {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 6%, transparent);
}

.home__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
}

.home__tag {
  padding: 3px 10px;
  border: none;
  border-radius: 999px;
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  cursor: pointer;
}

.home__tag:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.home__tag--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.home__kb-lock {
  font-size: var(--xl-fs-caption);
}

.home__sentinel {
  height: 1px;
}

.home__load-more {
  min-height: 34px;
  padding: 14px 0 4px;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  text-align: center;
}

@media (width <= 900px) {
  .home__glow {
    grid-template-columns: 1fr;
    gap: var(--xl-space-6);
    min-height: 0;
  }

  .home__glow-right {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid var(--xl-border);
    padding-top: var(--xl-space-4);
  }

  .home__glow-right::before,
  .glow-entry::before {
    display: none;
  }
}

@media (width <= 800px) {
  .home__layout {
    flex-direction: column;
  }

  .home__side {
    width: 100%;
    position: static;
  }

  .home__glow-search-input {
    max-width: none;
  }
}
</style>
