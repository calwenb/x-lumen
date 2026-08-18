<script setup lang="ts">
// 我的收藏（F-0212，PROTOTYPE B23）：登录用户收藏知识卡片流。
// 卡片：标题（点击进详情）/作者/所属库/摘要/收藏时间 + 取消收藏（toggle 成功后本地移除并刷新计数）。
// 状态：加载骨架、空态（引导去知识库发现页 /knowledge-bases）、失败重试；分页复用 publishing Pagination。
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'

import { fetchFavorites, toggleFavorite } from '@/modules/engagement/api/engagement'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { FavoriteItem } from '@/modules/engagement/api/engagement'

const PAGE_SIZE = 10

const favorites = ref<FavoriteItem[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)
const removingId = ref<string | null>(null)

/** 收藏时间展示：yyyy-MM-dd HH:mm（后端 ISO 本地时间字符串）。 */
function formatDateTime(iso: string): string {
  return iso.replace('T', ' ').slice(0, 16)
}

async function load(targetPage = 1): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchFavorites(targetPage, PAGE_SIZE)
    favorites.value = page.records
    total.value = page.total
    pageNo.value = page.pageNo
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 取消收藏：toggle 成功后从列表移除并同步计数；当前页清空且非首页时回退一页重查。 */
async function removeFavorite(item: FavoriteItem): Promise<void> {
  if (removingId.value) return
  removingId.value = item.id
  try {
    const confirmed = await toggleFavorite(item.id)
    if (confirmed) return // 服务端仍为已收藏（语义异常）：不动列表
    favorites.value = favorites.value.filter((row) => row.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    if (favorites.value.length === 0 && pageNo.value > 1) {
      await load(pageNo.value - 1)
    }
  } catch {
    ElMessage.error('取消收藏失败，请稍后重试')
  } finally {
    removingId.value = null
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <main class="favorites">
    <header class="favorites__header">
      <h1 class="favorites__title">我的收藏</h1>
      <p class="favorites__subtitle">共 {{ total }} 篇收藏知识</p>
    </header>

    <div v-if="loading" class="favorites__state">
      <div v-for="i in 3" :key="i" class="favorites__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="favorites__state">
      <p class="favorites__state-text">收藏列表加载失败</p>
      <el-button type="primary" plain @click="load(pageNo)">重试</el-button>
    </div>
    <div v-else-if="favorites.length === 0" class="favorites__state">
      <p class="favorites__state-text">还没有收藏任何知识。</p>
      <RouterLink class="favorites__guide" :to="{ name: 'kb-discovery' }">去知识库逛逛 →</RouterLink>
    </div>
    <template v-else>
      <article v-for="item in favorites" :key="item.id" class="favorite-card">
        <div class="favorite-card__main">
          <RouterLink class="favorite-card__title" :to="'/knowledge/' + item.id">
            {{ item.title }}
          </RouterLink>
          <p class="favorite-card__summary">{{ item.summary }}</p>
          <div class="favorite-card__meta">
            <span v-if="item.kbName" class="favorite-card__kb">{{ item.kbName }}</span>
            <span>{{ item.authorName }}</span>
            <span>收藏于 {{ formatDateTime(item.favoritedAt) }}</span>
          </div>
        </div>
        <el-button
          class="favorite-card__remove"
          text
          type="danger"
          :disabled="removingId !== null"
          @click="removeFavorite(item)"
        >
          {{ removingId === item.id ? '取消中' : '取消收藏' }}
        </el-button>
      </article>
      <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @change="load" />
    </template>
  </main>
</template>

<style scoped>
.favorites {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.favorites__header {
  margin-bottom: var(--xl-space-4);
}

.favorites__title {
  margin: 0 0 var(--xl-space-1);
  color: var(--xl-text-primary);
  font-size: 22px;
}

.favorites__subtitle {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: 13px;
}

.favorites__state {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-3);
  align-items: center;
  padding: var(--xl-space-8) 0;
}

.favorites__skeleton {
  width: 100%;
  height: 90px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.favorites__state-text {
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.favorites__guide {
  padding: 6px 16px;
  border: 1px solid var(--xl-color-primary);
  border-radius: 8px;
  color: var(--xl-color-primary);
  font-size: 13px;
  text-decoration: none;
}

.favorites__guide:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
}

.favorite-card {
  display: flex;
  gap: var(--xl-space-3);
  align-items: flex-start;
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

.favorite-card:hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.favorite-card__main {
  flex: 1;
  min-width: 0;
}

.favorite-card__title {
  color: var(--xl-text-primary);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
}

.favorite-card__title:hover {
  color: var(--xl-color-primary);
}

.favorite-card__summary {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  margin: var(--xl-space-2) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.favorite-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  align-items: center;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.favorite-card__kb {
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.favorite-card__remove {
  flex-shrink: 0;
}
</style>
