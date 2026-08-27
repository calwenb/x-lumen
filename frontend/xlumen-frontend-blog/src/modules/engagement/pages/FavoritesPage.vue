<script setup lang="ts">
// 我的收藏（PROTOTYPE B23）：登录用户收藏知识卡片流。
// 卡片：标题（点击进详情）/作者/所属库/摘要/收藏时间 + 取消收藏（toggle 成功后本地移除并刷新计数）。
// 状态：加载骨架、空态（引导去知识库发现页 /knowledge-bases）、失败重试；滚动触底自动追加。
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'

import { fetchFavorites, toggleFavorite } from '@/modules/engagement/api/engagement'
import { useInfinitePage } from '@/composables/useInfinitePage'
import InitialAvatar from '@/components/InitialAvatar.vue'

import type { FavoriteItem } from '@/modules/engagement/api/engagement'

const PAGE_SIZE = 10

const sentinel = ref<HTMLElement | null>(null)
const removingId = ref<string | null>(null)

/** 收藏时间展示：yyyy-MM-dd HH:mm（后端 ISO 本地时间字符串）。 */
function formatDateTime(iso: string): string {
  return iso.replace('T', ' ').slice(0, 16)
}

const infinite = useInfinitePage<FavoriteItem>({
  sentinel,
  pageSize: PAGE_SIZE,
  loadPage: (pageNo, pageSize) => fetchFavorites(pageNo, pageSize),
})

const favorites = infinite.items
const total = infinite.total
const loading = infinite.loading
const loadError = infinite.error

/** 取消收藏：toggle 成功后从列表移除并同步计数；当前页清空且非首页时回退一页重查。 */
async function removeFavorite(item: FavoriteItem): Promise<void> {
  if (removingId.value) return
  removingId.value = item.id
  try {
    const confirmed = await toggleFavorite(item.id)
    if (confirmed) return // 服务端仍为已收藏（语义异常）：不动列表
    favorites.value = favorites.value.filter((row) => row.id !== item.id)
    total.value = Math.max(0, total.value - 1)
  } catch {
    ElMessage.error('取消收藏失败，请稍后重试')
  } finally {
    removingId.value = null
  }
}

onMounted(() => {
  void infinite.loadFirst()
})
</script>

<template>
  <main class="favorites">
    <!-- 窄日期轨 -->
    <aside class="favorites__rail">
      <h1 class="favorites__title">我的收藏</h1>
      <p class="favorites__subtitle">共 {{ total }} 篇收藏知识</p>
    </aside>

    <!-- 宽收藏流 -->
    <section class="favorites__stream">
      <div v-if="loading" class="favorites__state">
        <div v-for="i in 3" :key="i" class="favorites__skeleton" aria-hidden="true" />
      </div>
      <div v-else-if="loadError" class="favorites__state">
        <p class="favorites__state-text">收藏列表加载失败</p>
        <el-button type="primary" plain @click="infinite.retry()">重试</el-button>
      </div>
      <div v-else-if="favorites.length === 0" class="favorites__state">
        <p class="favorites__state-text">还没有收藏任何知识。</p>
        <RouterLink class="favorites__guide" :to="{ name: 'kb-discovery' }"
          >去知识库逛逛 →</RouterLink
        >
      </div>
      <template v-else>
        <article v-for="item in favorites" :key="item.id" class="favorite-card">
          <RouterLink class="favorite-card__title" :to="'/knowledge/' + item.id">
            {{ item.title }}
          </RouterLink>
          <p class="favorite-card__summary">{{ item.summary }}</p>
          <div class="favorite-card__meta">
            <InitialAvatar :name="item.authorName" :size="20" />
            <span class="favorite-card__author">{{ item.authorName }}</span>
            <span v-if="item.kbName" class="favorite-card__kb">{{ item.kbName }}</span>
            <span>收藏于 {{ formatDateTime(item.favoritedAt) }}</span>
          </div>
          <div class="favorite-card__foot">
            <el-button
              class="favorite-card__remove"
              text
              type="danger"
              :disabled="removingId !== null"
              @click="removeFavorite(item)"
            >
              {{ removingId === item.id ? '取消中' : '取消收藏' }}
            </el-button>
          </div>
        </article>
        <div ref="sentinel" class="favorites__sentinel" aria-hidden="true" />
        <div v-if="infinite.loadingMore" class="favorites__load-more" role="status">加载更多…</div>
        <div v-else-if="infinite.loadMoreError" class="favorites__load-more">
          <el-button type="primary" plain size="small" @click="infinite.retryMore()"
            >重试加载</el-button
          >
        </div>
        <div v-else-if="!infinite.hasMore" class="favorites__load-more">已加载全部收藏</div>
      </template>
    </section>
  </main>
</template>

<style scoped>
.favorites {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: var(--xl-space-8);
  align-items: start;
  width: min(calc(100% - 48px), var(--xl-container));
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

.favorites__rail {
  position: sticky;
  top: calc(var(--xl-header-h) + var(--xl-space-6));
  padding-top: var(--xl-space-6);
  border-top: 2px solid var(--xl-color-primary);
}

.favorites__title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.favorites__subtitle {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.favorites__stream {
  min-width: 0;
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
  font-size: var(--xl-fs-body);
}

.favorites__sentinel {
  height: 1px;
}

.favorites__load-more {
  min-height: 34px;
  padding: 14px 0 4px;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  text-align: center;
}

.favorites__guide {
  padding: 6px 16px;
  border: 1px solid var(--xl-color-primary);
  border-radius: var(--xl-radius);
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-caption);
  text-decoration: none;
}

.favorites__guide:hover {
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
}

/* ===== 收藏流条目 ===== */
.favorite-card {
  display: flex;
  flex-direction: column;
  padding: var(--xl-space-4) 0;
  border-bottom: 1px solid var(--xl-border);
}

.favorite-card__title {
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  text-decoration: none;
  transition: color var(--xl-transition);
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
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

.favorite-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.favorite-card__author {
  color: var(--xl-text-secondary);
  font-weight: 500;
}

.favorite-card__kb {
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.favorite-card__foot {
  margin-top: var(--xl-space-2);
}

.favorite-card__remove {
  padding: 0;
  font-size: var(--xl-fs-caption);
  opacity: 0.75;
}

.favorite-card__remove:hover {
  opacity: 1;
}

@media (width <= 800px) {
  .favorites {
    grid-template-columns: 1fr;
  }

  .favorites__rail {
    position: static;
  }
}
</style>
