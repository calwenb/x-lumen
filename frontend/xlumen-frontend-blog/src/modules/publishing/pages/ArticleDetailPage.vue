<script setup lang="ts">
// 文章详情（B02，F-0201/F-0203）：标题/作者/时间/阅读时间/标签 + 目录导航 + Markdown 正文 + 点赞/评论。
// 关键状态：加载骨架、404 不可访问解释、失败可重试；进入页面上报一次阅读量（防刷由后端保证）。
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import CommentList from '@/modules/engagement/components/CommentList.vue'
import FeedbackDialog from '@/modules/engagement/components/FeedbackDialog.vue'
import LikeButton from '@/modules/engagement/components/LikeButton.vue'
import ArticleQaDialog from '@/modules/chat/components/ArticleQaDialog.vue'
import { fetchArticle, reportView } from '@/modules/publishing/api/public'
import { extractToc, renderMarkdown } from '@/modules/publishing/utils/markdown'

import type { ArticleDetail } from '@/modules/publishing/api/public'
import type { TocItem } from '@/modules/publishing/utils/markdown'

const route = useRoute()

const article = ref<ArticleDetail | null>(null)
const loading = ref(true)
const loadError = ref(false)
const notFound = ref(false)
const commentCount = ref(0)

const articleId = computed(() => String(route.params.id))
const toc = computed<TocItem[]>(() => (article.value ? extractToc(article.value.content) : []))
const renderedHtml = computed(() => (article.value ? renderMarkdown(article.value.content) : ''))
const updatedAt = computed(() => (article.value ? formatDate(article.value.updatedAt) : ''))

// D02 文章级问答与 F-1001 读者纠错弹窗
const showQa = ref(false)
const showFeedback = ref(false)

function formatDate(iso: string): string {
  return iso.slice(0, 10)
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  notFound.value = false
  try {
    article.value = await fetchArticle(articleId.value)
    commentCount.value = article.value.commentCount
  } catch (error) {
    // 后端 404（NOT_FOUND）统一提示不可访问解释；其余按加载失败可重试处理
    if (error instanceof Error && error.message.includes('不存在')) {
      notFound.value = true
    } else {
      loadError.value = true
    }
  } finally {
    loading.value = false
  }
}

/** 目录点击：滚动到对应标题（id 与渲染标题文本一致）。 */
function scrollToAnchor(anchor: string): void {
  document.getElementById(anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  await load()
  if (!notFound.value && !loadError.value) {
    // 阅读量上报：失败不影响阅读（F-0203）
    reportView(articleId.value).catch(() => undefined)
  }
})
</script>

<template>
  <main class="detail">
    <div v-if="loading" class="detail__state">
      <div class="detail__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="notFound" class="detail__state">
      <h1 class="detail__state-title">文章不可访问</h1>
      <p class="detail__state-text">文章不存在、已下架或未公开（F-0307）。</p>
      <RouterLink class="detail__back" to="/">返回首页</RouterLink>
    </div>
    <div v-else-if="loadError || !article" class="detail__state">
      <p class="detail__state-text">文章加载失败</p>
      <button type="button" class="detail__retry" @click="load">重试</button>
    </div>
    <div v-else class="detail__layout">
      <aside v-if="toc.length > 0" class="detail__toc">
        <h2 class="detail__toc-title">目录</h2>
        <button
          v-for="item in toc"
          :key="item.anchor"
          type="button"
          class="detail__toc-item"
          :class="`detail__toc-item--${item.level}`"
          @click="scrollToAnchor(item.anchor)"
        >
          {{ item.text }}
        </button>
      </aside>

      <article class="detail__article">
        <header class="detail__header">
          <h1 class="detail__title">{{ article.title }}</h1>
          <div class="detail__meta">
            <span>{{ article.authorName }}</span>
            <span>发布于 {{ formatDate(article.publishedAt) }}</span>
            <span v-if="updatedAt !== formatDate(article.publishedAt)">更新于 {{ updatedAt }}</span>
            <span>{{ article.readMinutes }} 分钟阅读</span>
            <span>{{ article.viewCount }} 阅读</span>
          </div>
          <div class="detail__tags">
            <RouterLink
              v-if="article.category"
              class="detail__tag"
              :to="`/search?category=${encodeURIComponent(article.category)}`"
            >
              {{ article.category }}
            </RouterLink>
            <RouterLink
              v-for="tag in article.tags"
              :key="tag"
              class="detail__tag"
              :to="`/search?tag=${encodeURIComponent(tag)}`"
            >
              # {{ tag }}
            </RouterLink>
          </div>
        </header>

        <div class="markdown-body" v-html="renderedHtml" />

        <div class="detail__actions">
          <LikeButton
            :article-id="article.id"
            :initial="article.liked"
            :count="article.likeCount"
            @update:count="article.likeCount = $event"
          />
          <button type="button" class="detail__action-button" @click="showQa = true">问「小光」</button>
          <button type="button" class="detail__action-button" @click="showFeedback = true">纠错反馈</button>
          <span class="detail__actions-hint">登录后可点赞与评论</span>
        </div>

        <CommentList :article-id="article.id" @update:count="commentCount = $event" />
      </article>
    </div>

    <ArticleQaDialog
      v-if="showQa && article"
      :article-id="article.id"
      :article-title="article.title"
      @close="showQa = false"
    />
    <FeedbackDialog v-if="showFeedback && article" :article-id="article.id" @close="showFeedback = false" />
  </main>
</template>

<style scoped>
.detail {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.detail__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
}

.detail__skeleton {
  height: 300px;
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.detail__state-title {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 22px;
}

.detail__state-text {
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.detail__back,
.detail__retry {
  display: inline-block;
  margin-top: var(--xl-space-4);
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
}

.detail__layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 720px);
  gap: var(--xl-space-6);
  justify-content: center;
  align-items: start;
}

.detail__toc {
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 96px);
  overflow-y: auto;
}

.detail__toc-title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: 14px;
}

.detail__toc-item {
  display: block;
  width: 100%;
  padding: 5px 0;
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}

.detail__toc-item:hover {
  color: var(--xl-color-primary);
}

.detail__toc-item--3 {
  padding-left: var(--xl-space-3);
}

.detail__toc-item--4 {
  padding-left: var(--xl-space-6);
}

.detail__article {
  min-width: 0;
}

.detail__header {
  padding-bottom: var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
}

.detail__title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: 28px;
  line-height: 1.4;
}

.detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  color: var(--xl-text-muted);
  font-size: 13px;
}

.detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-3);
}

.detail__tag {
  color: var(--xl-color-primary);
  font-size: 12px;
  text-decoration: none;
}

.detail__actions {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  margin-top: var(--xl-space-6);
}

.detail__actions-hint {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.detail__action-button {
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.detail__action-button:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

/* Markdown 正文样式（B02）：与设计 token 对齐，代码块等保持可读 */
.markdown-body {
  color: var(--xl-text-primary);
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: break-word;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.6em 0 0.6em;
  color: var(--xl-text-primary);
  scroll-margin-top: 72px;
}

.markdown-body :deep(p) {
  margin: 0.8em 0;
}

.markdown-body :deep(a) {
  color: var(--xl-color-primary);
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--xl-border) 70%, transparent);
  font-family: var(--xl-font-mono);
  font-size: 13px;
}

.markdown-body :deep(pre) {
  padding: var(--xl-space-4);
  overflow-x: auto;
  border-radius: var(--xl-radius-card);
  background: #1f2937;
  color: #f9fafb;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  margin: 1em 0;
  padding: 0 var(--xl-space-4);
  border-left: 3px solid var(--xl-color-primary);
  color: var(--xl-text-secondary);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.6em;
}

@media (width <= 900px) {
  .detail__layout {
    grid-template-columns: 1fr;
  }

  .detail__toc {
    display: none;
  }
}
</style>
