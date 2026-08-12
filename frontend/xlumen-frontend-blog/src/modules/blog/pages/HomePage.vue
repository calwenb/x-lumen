<script setup lang="ts">
// 博客首页（B01，F-0201/F-0202）：最新公开文章列表 + 分类/标签侧栏。
// 关键状态：加载骨架、无文章空态、失败可重试；私有/草稿文章由后端过滤（F-0307）。
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchArticles, fetchCategories, fetchTags } from '@/modules/publishing/api/public'
import Pagination from '@/modules/publishing/components/Pagination.vue'

import type { ArticleCard, CategoryCount } from '@/modules/publishing/api/public'

const PAGE_SIZE = 10

const articles = ref<ArticleCard[]>([])
const categories = ref<CategoryCount[]>([])
const tags = ref<CategoryCount[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)

/** 日期显示：yyyy-MM-dd。 */
function formatDate(iso: string): string {
  return iso.slice(0, 10)
}

async function load(targetPage: number): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchArticles({ pageNo: targetPage, pageSize: PAGE_SIZE })
    articles.value = page.records
    total.value = page.total
    pageNo.value = page.pageNo
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(1), fetchCategories().then((list) => (categories.value = list)), fetchTags().then((list) => (tags.value = list))])
})
</script>

<template>
  <main class="home">
    <div class="home__layout">
      <section class="home__main">
        <h1 class="home__title">最新文章</h1>

        <div v-if="loading" class="home__state">
          <div v-for="i in 3" :key="i" class="home__skeleton" aria-hidden="true" />
        </div>
        <div v-else-if="loadError" class="home__state">
          <p class="home__state-text">文章加载失败</p>
          <button type="button" class="home__retry" @click="load(pageNo)">重试</button>
        </div>
        <template v-else>
          <p v-if="articles.length === 0" class="home__state-text">还没有文章，敬请期待。</p>
          <article v-for="article in articles" v-else :key="article.id" class="article-card">
            <RouterLink class="article-card__title" :to="`/articles/${article.id}`">
              {{ article.title }}
            </RouterLink>
            <p class="article-card__summary">{{ article.summary }}</p>
            <div class="article-card__meta">
              <span>{{ article.authorName }}</span>
              <span>{{ formatDate(article.publishedAt) }}</span>
              <span>{{ article.readMinutes }} 分钟阅读</span>
              <span>{{ article.viewCount }} 阅读</span>
              <span>{{ article.commentCount }} 评论</span>
              <span>{{ article.likeCount }} 点赞</span>
            </div>
            <div class="article-card__tags">
              <RouterLink v-if="article.category" class="article-card__tag" :to="`/search?category=${encodeURIComponent(article.category)}`">
                {{ article.category }}
              </RouterLink>
              <RouterLink
                v-for="tag in article.tags"
                :key="tag"
                class="article-card__tag"
                :to="`/search?tag=${encodeURIComponent(tag)}`"
              >
                # {{ tag }}
              </RouterLink>
            </div>
          </article>
          <Pagination :page-no="pageNo" :page-size="PAGE_SIZE" :total="total" @change="load" />
        </template>
      </section>

      <aside class="home__side">
        <section v-if="categories.length > 0" class="side-card">
          <h2 class="side-card__title">分类</h2>
          <RouterLink
            v-for="category in categories"
            :key="category.name"
            class="side-card__item"
            :to="`/search?category=${encodeURIComponent(category.name)}`"
          >
            <span>{{ category.name }}</span>
            <span class="side-card__count">{{ category.count }}</span>
          </RouterLink>
        </section>
        <section v-if="tags.length > 0" class="side-card">
          <h2 class="side-card__title">标签</h2>
          <div class="side-card__tags">
            <RouterLink
              v-for="tag in tags"
              :key="tag.name"
              class="side-card__tag"
              :to="`/search?tag=${encodeURIComponent(tag.name)}`"
            >
              # {{ tag.name }}
            </RouterLink>
          </div>
        </section>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.home {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.home__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: var(--xl-space-6);
  align-items: start;
}

.home__main {
  min-width: 0;
}

.home__title {
  margin: 0 0 var(--xl-space-4);
  color: var(--xl-text-primary);
  font-size: 20px;
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

.article-card {
  padding: var(--xl-space-4) 0;
  border-bottom: 1px solid var(--xl-border);
}

.article-card__title {
  color: var(--xl-text-primary);
  font-size: 18px;
  font-weight: 600;
  text-decoration: none;
}

.article-card__title:hover {
  color: var(--xl-color-primary);
}

.article-card__summary {
  margin: var(--xl-space-2) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.article-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: 12px;
}

.article-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-2);
}

.article-card__tag {
  color: var(--xl-color-primary);
  font-size: 12px;
  text-decoration: none;
}

.article-card__tag:hover {
  text-decoration: underline;
}

.home__side {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.side-card {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.side-card__title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: 15px;
}

.side-card__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-decoration: none;
}

.side-card__item:hover {
  color: var(--xl-color-primary);
}

.side-card__count {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.side-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
}

.side-card__tag {
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-decoration: none;
}

.side-card__tag:hover {
  color: var(--xl-color-primary);
}

@media (width <= 800px) {
  .home__layout {
    grid-template-columns: 1fr;
  }
}
</style>
