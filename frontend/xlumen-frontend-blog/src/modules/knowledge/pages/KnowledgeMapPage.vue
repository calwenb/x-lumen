<script setup lang="ts">
// 知识地图：全站公开知识的总览页，访客可见。
// 已登录且公开知识 ≥3 篇时，调 AI 做主题聚类（assist kb_cluster），按主题卡片展示；
// 未登录、知识不足或聚类失败时，回退为按知识库分组的静态文档网格，均可点击跳转详情。
// AI 聚类为增强能力，任何失败都不阻断页面展示。
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRouter } from 'vue-router'
import { MapLocation } from '@element-plus/icons-vue'

import { assistAction } from '@/modules/ai/api/assist'
import { fetchKnowledges } from '@/modules/publishing/api/public'
import { useSessionStore } from '@/stores/session'

import type { KnowledgeCard } from '@/modules/publishing/api/public'

const router = useRouter()
const session = useSessionStore()

const knowledges = ref<KnowledgeCard[]>([])
const loading = ref(true)
const loadError = ref(false)

/** AI 聚类进行中（不阻断页面，期间先展示静态分组）。 */
const clustering = ref(false)
/** AI 聚类结果（[{topic, ids}]）；null 表示未启用或失败，走静态按库分组。 */
const aiThemes = ref<ClusterTheme[] | null>(null)

interface ClusterTheme {
  topic: string
  ids: string[]
}

const PAGE_SIZE = 100

function formatDate(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 10) : ''
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchKnowledges({ pageNo: 1, pageSize: PAGE_SIZE })
    knowledges.value = page.records
  } catch {
    loadError.value = true
    return
  } finally {
    loading.value = false
  }
  if (knowledgeCount.value >= 3 && session.loggedIn) {
    void runClustering()
  }
}

const knowledgeCount = computed(() => knowledges.value.length)

/** 聚类输入：每行「序号. 标题——摘要」，序号与后端返回的 ids 一一对应（1 起）。 */
function buildClusterContent(): string {
  return knowledges.value
    .map((knowledge, index) => `${index + 1}. ${knowledge.title}——${knowledge.summary}`)
    .join('\n')
}

async function runClustering(): Promise<void> {
  clustering.value = true
  try {
    const raw = await assistAction({ action: 'kb_cluster', content: buildClusterContent() })
    aiThemes.value = parseThemes(raw)
  } catch {
    aiThemes.value = null
    ElMessage.warning('AI 主题聚类暂不可用，已按知识库为你整理')
  } finally {
    clustering.value = false
  }
}

/** 解析 AI 返回的 JSON 数组（[{topic, ids}]，ids 为数字序号字符串）；结构不符返回 null。 */
function parseThemes(raw: string): ClusterTheme[] | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return null
  }
  if (!Array.isArray(parsed)) return null
  const themes: ClusterTheme[] = []
  for (const item of parsed) {
    if (typeof item !== 'object' || item === null) continue
    const candidate = item as { topic?: unknown; ids?: unknown }
    if (typeof candidate.topic !== 'string' || !candidate.topic.trim()) continue
    if (!Array.isArray(candidate.ids)) continue
    const numericIds = candidate.ids.map((id) => String(id)).filter((id) => /^\d+$/.test(id))
    if (numericIds.length === 0) continue
    themes.push({ topic: candidate.topic.trim(), ids: numericIds })
  }
  return themes.length > 0 ? themes : null
}

/** AI 主题视图：把数字序号映射回知识记录，越界/无效序号自动丢弃。 */
const themeGroups = computed(() => {
  if (!aiThemes.value) return []
  return aiThemes.value
    .map((theme) => ({
      topic: theme.topic,
      items: theme.ids
        .map((id) => knowledges.value[Number(id) - 1])
        .filter((item): item is KnowledgeCard => Boolean(item)),
    }))
    .filter((group) => group.items.length > 0)
})

interface KbGroup {
  kbId: string
  kbName: string
  items: KnowledgeCard[]
}

/** 静态视图：按知识库分组（kbName 缺失归入「未分组知识」）。 */
const kbGroups = computed<KbGroup[]>(() => {
  const map = new Map<string, KbGroup>()
  for (const knowledge of knowledges.value) {
    const key = knowledge.kbId || ''
    const group = map.get(key)
    if (group) {
      group.items.push(knowledge)
    } else {
      map.set(key, {
        kbId: key,
        kbName: knowledge.kbName || '未分组知识',
        items: [knowledge],
      })
    }
  }
  return [...map.values()]
})

/** 聚类有结果时采用主题视图，否则静态按库分组（含聚类进行中/失败）。 */
const useThemeView = computed(() => themeGroups.value.length > 0)

function openKnowledge(id: string): void {
  void router.push(`/knowledge/${id}`)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="map">
    <header class="map__head">
      <h1 class="map__title">
        <el-icon class="map__title-icon"><MapLocation /></el-icon>
        知识地图
      </h1>
      <p class="map__desc">总览全部公开知识：已登录时 AI 按主题自动聚类，否则按知识库归档展示。</p>
      <p v-if="clustering" class="map__hint" role="status">✦ 小光正在为主题聚类…</p>
    </header>

    <div v-if="loading" class="map__state">
      <div v-for="i in 4" :key="i" class="map__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="map__state">
      <p class="map__state-text">知识地图加载失败</p>
      <el-button type="primary" plain @click="load">重试</el-button>
    </div>
    <div v-else-if="knowledgeCount === 0" class="map__state">
      <p class="map__state-text">还没有公开知识，敬请期待。</p>
    </div>
    <!-- 主题航道：登录态 AI 聚类结果 -->
    <section v-else-if="useThemeView" class="map__lanes">
      <article v-for="group in themeGroups" :key="group.topic" class="map__col">
        <header class="map__col-head">
          <span class="map__col-star" aria-hidden="true">✦</span>
          <h2 class="map__col-title">{{ group.topic }}</h2>
        </header>
        <ul class="map__col-list">
          <li
            v-for="item in group.items"
            :key="item.id"
            class="map__col-item"
            @click="openKnowledge(item.id)"
          >
            <RouterLink class="map__col-link" :to="`/knowledge/${item.id}`">
              {{ item.title }}
            </RouterLink>
            <p class="map__col-summary">{{ item.summary }}</p>
            <div class="map__col-meta">
              <el-tag v-if="item.kbName" size="small" effect="plain">{{ item.kbName }}</el-tag>
              <span v-if="formatDate(item.publishedAt)">{{ formatDate(item.publishedAt) }}</span>
            </div>
          </li>
        </ul>
      </article>
    </section>
    <!-- 知识库航道：访客 / 无聚类结果时 -->
    <section v-else class="map__lanes">
      <article v-for="group in kbGroups" :key="group.kbId" class="map__col">
        <header class="map__col-head map__col-head--kb">
          <h2 class="map__col-title">{{ group.kbName }}</h2>
        </header>
        <ul class="map__col-list">
          <li
            v-for="item in group.items"
            :key="item.id"
            class="map__col-item"
            @click="openKnowledge(item.id)"
          >
            <RouterLink class="map__col-link" :to="`/knowledge/${item.id}`">
              {{ item.title }}
            </RouterLink>
            <p class="map__col-summary">{{ item.summary }}</p>
            <div class="map__col-meta">
              <el-tag v-if="item.kbName" size="small" effect="plain">{{ item.kbName }}</el-tag>
              <span>{{ formatDate(item.publishedAt) }}</span>
            </div>
          </li>
        </ul>
      </article>
    </section>
  </main>
</template>

<style scoped>
.map {
  width: min(calc(100% - 48px), var(--xl-container));
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

.map__head {
  margin-bottom: var(--xl-space-8);
}

.map__title {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.map__title-icon {
  color: var(--xl-color-primary);
  font-size: 24px;
}

.map__desc {
  max-width: 640px;
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

.map__hint {
  margin: var(--xl-space-3) 0 0;
  color: var(--xl-color-ai);
  font-size: var(--xl-fs-caption);
}

.map__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
}

.map__skeleton {
  height: 96px;
  margin-bottom: var(--xl-space-4);
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.map__state-text {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

/* ===== 主题航道：横向错落的纵向栏 ===== */
.map__lanes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-4);
  align-items: flex-start;
}

.map__col {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.map__col-head {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding-bottom: var(--xl-space-3);
  border-bottom: 1px solid var(--xl-border);
}

.map__col-head--kb {
  border-left: 3px solid var(--xl-color-primary);
  padding-left: var(--xl-space-3);
}

.map__col-star {
  color: var(--xl-color-ai);
  font-size: 15px;
}

.map__col-title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  overflow-wrap: break-word;
}

.map__col-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.map__col-item {
  padding: var(--xl-space-3) 0;
  cursor: pointer;
}

.map__col-item:last-child {
  padding-bottom: 0;
}

.map__col-link {
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
  font-weight: 600;
  text-decoration: none;
  overflow-wrap: break-word;
  transition: color var(--xl-transition);
}

.map__col-item:hover .map__col-link {
  color: var(--xl-color-primary);
}

.map__col-summary {
  margin: 4px 0 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.map__col-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: 6px;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

@media (width <= 960px) {
  .map__lanes {
    flex-direction: column;
  }

  .map__col {
    width: 100%;
  }
}
</style>
