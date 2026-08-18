<script setup lang="ts">
// 首页知识流（B01，F-0201/F-0202/F-0208，KB-4 知识平台化）：左栏库导航 + 右栏知识卡片流。
// 左栏：库切换器（全部知识库/我的知识库）；选中库后切换为该库目录树 + 标签云；未选库时显示公开知识库列表与「我的知识库」入口。
// 简化决策：目录树/标签云/库切换仅在登录态可用（后端 /knowledge-bases 为鉴权接口），未登录首页为纯列表流。
// 排序由后端保证：未选目录按更新时间倒序，选中目录后按创建时间正序。私有库知识卡片 🔒 由前端比对登录用户私有库集合标记。
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowDown, Document } from '@element-plus/icons-vue'

import { fetchDirectoryTree, fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { fetchKnowledges, fetchTags } from '@/modules/publishing/api/public'
import DirectoryTreeContextMenu from '@/modules/knowledge/components/DirectoryTreeContextMenu.vue'
import Pagination from '@/modules/publishing/components/Pagination.vue'
import { useSessionStore } from '@/stores/session'

import type { DirectoryNode, KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import type { CategoryCount, KnowledgeCard } from '@/modules/publishing/api/public'

const PAGE_SIZE = 10

const session = useSessionStore()
const router = useRouter()

// 右栏列表状态
const knowledges = ref<KnowledgeCard[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)

// 左栏范围状态（空 kbId = 全部知识库）
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

// F-0312 库主判定：左栏库切换器数据源为 fetchKnowledgeBases（鉴权接口，仅返回登录用户自己的库），
// 选中库必然属于当前用户，故「已选中某库」即等价于库主，右键菜单可用。
const isKbOwner = computed(() => Boolean(selectedKb.value))

/** F-0312 右键菜单实例（open(event, node?) 由目录树 contextmenu 调用，node 省略 = 树根）。 */
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

async function load(targetPage: number): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchKnowledges({
      ...(selectedKbId.value ? { kbId: selectedKbId.value } : {}),
      ...(selectedDirectoryId.value ? { directoryId: selectedDirectoryId.value } : {}),
      ...(selectedTag.value ? { tag: selectedTag.value } : {}),
      pageNo: targetPage,
      pageSize: PAGE_SIZE,
    })
    knowledges.value = page.records
    total.value = page.total
    pageNo.value = page.pageNo
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

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
  await load(1)
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
  await load(1)
}

/** 标签筛选（再次点击取消）。 */
async function toggleTag(name: string): Promise<void> {
  selectedTag.value = selectedTag.value === name ? '' : name
  await load(1)
}

/** F-0312 右键菜单操作成功后刷新目录树（知识数随树节点返回；失败保留原树）。 */
async function refreshDirectories(): Promise<void> {
  if (!selectedKbId.value) return
  directoryTree.value = await fetchDirectoryTree(selectedKbId.value).catch(
    () => directoryTree.value,
  )
}

/** F-0312 删除目录后：选中目录在删除范围内则重置为「全部知识」，并重新拉取列表（知识上挂父目录）。 */
function onDirectoryDeleted(ids: string[]): void {
  if (selectedDirectoryId.value && ids.includes(selectedDirectoryId.value)) {
    selectedDirectoryId.value = ''
    selectedDirectoryName.value = ''
  }
  void load(1)
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
  await load(1)
})
</script>

<template>
  <main class="home">
    <div class="home-layout">
      <aside class="home__side">
        <!-- 库切换器（登录态）：全部知识库 + 我的知识库 -->
        <el-dropdown v-if="session.loggedIn" trigger="click" @command="switchKb">
          <button type="button" class="home__switcher">
            <span class="home__switcher-label">{{ scopeTitle }}</span>
            <el-icon class="home__switcher-icon"><ArrowDown /></el-icon>
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

        <!-- 未登录：纯列表流，左栏说明 -->
        <section v-else class="side-card home__guest-hint">
          <p class="home__guest-text">登录后可浏览知识库、目录与标签筛选。</p>
          <RouterLink class="home__guest-login" to="/login">登录 / 注册</RouterLink>
        </section>

        <template v-if="session.loggedIn">
          <!-- 选中库：目录树 + 标签云 -->
          <template v-if="selectedKbId">
            <section class="side-card" @contextmenu="dirMenu?.open($event)">
              <h2 class="side-card__title">目录</h2>
              <p v-if="sideLoading" class="side-card__hint">目录加载中…</p>
              <p v-else-if="directoryTree.length === 0" class="side-card__hint">该知识库暂无目录</p>
              <ul v-else class="side-card__tree">
                <li v-for="node in flattenTree(directoryTree)" :key="node.id">
                  <button
                    type="button"
                    class="side-card__dir"
                    :class="{ 'side-card__dir--active': node.id === selectedDirectoryId }"
                    :style="{ paddingLeft: `${10 + node.depth * 14}px` }"
                    @click="toggleDirectory(node)"
                    @contextmenu.stop="dirMenu?.open($event, { id: node.id, name: node.name })"
                  >
                    <span>{{ node.name }}</span>
                    <span class="side-card__count">{{ node.knowledgeCount }}</span>
                  </button>
                </li>
              </ul>
            </section>
            <section v-if="tags.length > 0" class="side-card">
              <h2 class="side-card__title">标签云</h2>
              <div class="side-card__tags">
                <button
                  v-for="tag in tags"
                  :key="tag.name"
                  type="button"
                  class="side-card__tag"
                  :class="{ 'side-card__tag--active': tag.name === selectedTag }"
                  @click="toggleTag(tag.name)"
                >
                  # {{ tag.name }}
                </button>
              </div>
            </section>
          </template>

          <!-- 全部知识库：公开库列表 + 我的知识库入口 -->
          <template v-else>
            <section class="side-card">
              <h2 class="side-card__title">公开知识库</h2>
              <p v-if="publicKnowledgeBases.length === 0" class="side-card__hint">暂无公开知识库</p>
              <RouterLink
                v-for="kb in publicKnowledgeBases"
                :key="kb.id"
                class="side-card__item"
                :to="`/kb/${kb.id}`"
              >
                <span>{{ kb.name }}</span>
                <span class="side-card__count">{{ kb.knowledgeCount }}</span>
              </RouterLink>
            </section>
            <RouterLink
              class="side-card__mine"
              :to="{ name: 'kb-discovery', query: { mine: '1' } }"
            >
              我的知识库 →
            </RouterLink>
          </template>
        </template>
      </aside>

      <section class="home__main">
        <header class="home__list-head">
          <h1 class="home__title">{{ scopeTitle }}</h1>
          <p class="home__sort">更新时间倒序 · 选中目录后按创建时间正序 · 共 {{ total }} 篇</p>
        </header>

        <div v-if="loading" class="home__state">
          <div v-for="i in 3" :key="i" class="home__skeleton" aria-hidden="true" />
        </div>
        <div v-else-if="loadError" class="home__state">
          <p class="home__state-text">知识加载失败</p>
          <button type="button" class="home__retry" @click="load(pageNo)">重试</button>
        </div>
        <template v-else>
          <div v-if="knowledges.length === 0" class="home__empty">
            <el-icon class="home__empty-icon"><Document /></el-icon>
            <p>{{ emptyText }}</p>
          </div>
          <article
            v-for="knowledge in knowledges"
            v-else
            :key="knowledge.id"
            class="knowledge-card"
            @click="openKnowledge(knowledge.id)"
          >
            <div class="knowledge-card__badges">
              <RouterLink
                v-if="knowledge.kbName"
                class="knowledge-card__kb"
                :to="`/kb/${knowledge.kbId}`"
                @click.stop
              >
                <el-tag size="small" round effect="plain">{{ knowledge.kbName }}</el-tag>
              </RouterLink>
              <el-tag
                v-if="isPrivateCard(knowledge)"
                class="knowledge-card__private"
                size="small"
                round
                effect="plain"
              >
                🔒 私有
              </el-tag>
            </div>
            <RouterLink
              class="knowledge-card__title"
              :to="`/knowledge/${knowledge.id}`"
              @click.stop
            >
              {{ knowledge.title }}
            </RouterLink>
            <p class="knowledge-card__summary">{{ knowledge.summary }}</p>
            <div class="knowledge-card__meta">
              <span>{{ knowledge.authorName }}</span>
              <span>{{ formatDate(knowledge.publishedAt) }}</span>
              <span>{{ knowledge.readMinutes }} 分钟阅读</span>
              <span>{{ knowledge.viewCount }} 阅读</span>
            </div>
            <div v-if="knowledge.tags.length > 0" class="knowledge-card__tags">
              <RouterLink
                v-for="tag in knowledge.tags"
                :key="tag"
                class="knowledge-card__tag"
                :to="`/search?tag=${encodeURIComponent(tag)}`"
                @click.stop
              >
                # {{ tag }}
              </RouterLink>
            </div>
          </article>
          <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @change="load" />
        </template>
      </section>
    </div>

    <!-- F-0312 目录树右键菜单（新增/重命名/删除，仅库主；open 非库主时忽略） -->
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
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.home-layout {
  display: flex;
  gap: var(--xl-space-6);
  align-items: flex-start;
}

.home__side {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
  position: sticky;
  top: var(--xl-space-6);
}

.home__main {
  flex: 1;
  min-width: 0;
}

.home__switcher {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color var(--xl-transition);
}

.home__switcher:hover {
  border-color: var(--xl-color-primary);
}

.home__switcher-icon {
  color: var(--xl-text-muted);
  font-size: 13px;
}

.home__list-head {
  margin-bottom: var(--xl-space-4);
}

.home__title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 20px;
}

.home__sort {
  margin: var(--xl-space-1) 0 0;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.home__guest-hint {
  text-align: center;
}

.home__guest-text {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.home__guest-login {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-size: 13px;
  text-decoration: none;
}

.home__guest-login:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 18%, transparent);
}

.home__state {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.home__skeleton {
  height: 120px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.home__state-text {
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.home__retry {
  align-self: flex-start;
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.home__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--xl-space-3);
  padding: var(--xl-space-8) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.home__empty-icon {
  font-size: 40px;
  color: var(--xl-text-muted);
}

.home__empty p {
  margin: 0;
}

.knowledge-card {
  padding: var(--xl-space-4) var(--xl-space-6);
  margin-bottom: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  cursor: pointer;
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.knowledge-card:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.knowledge-card__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-2);
}

.knowledge-card__kb {
  text-decoration: none;
}

.knowledge-card__private :deep(.el-tag__content) {
  color: #b7791f;
}

.knowledge-card__title {
  color: var(--xl-text-primary);
  font-size: 18px;
  font-weight: 600;
  text-decoration: none;
}

.knowledge-card__title:hover {
  color: var(--xl-color-primary);
}

.knowledge-card__summary {
  margin: var(--xl-space-2) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.knowledge-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: 12px;
}

.knowledge-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-3);
}

.knowledge-card__tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
  font-size: 12px;
  text-decoration: none;
}

.knowledge-card__tag:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 16%, transparent);
}

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
  font-size: 15px;
  font-weight: 600;
}

.side-card__hint {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.side-card__tree {
  list-style: none;
  margin: 0;
  padding: 0;
}

.side-card__dir {
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
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.side-card__dir:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.side-card__dir--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-weight: 600;
}

.side-card__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: var(--xl-radius-sm);
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-decoration: none;
}

.side-card__item:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.side-card__count {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.side-card__mine {
  display: block;
  padding: 8px 10px;
  border: 1px dashed var(--xl-border);
  border-radius: var(--xl-radius-card);
  color: var(--xl-color-primary);
  font-size: 13px;
  text-align: center;
  text-decoration: none;
}

.side-card__mine:hover {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 6%, transparent);
}

.side-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
}

.side-card__tag {
  padding: 3px 10px;
  border: none;
  border-radius: 999px;
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.side-card__tag:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.side-card__tag--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.home__kb-lock {
  font-size: 12px;
}

@media (width <= 800px) {
  .home-layout {
    flex-direction: column;
  }

  .home__side {
    width: 100%;
    position: static;
  }
}
</style>
