<script setup lang="ts">
// 知识详情（B02，F-0201/F-0203）：标题/作者/时间/阅读时间/标签 + 目录导航 + Markdown 正文 + 点赞/评论。
// 关键状态：加载骨架、404 不可访问解释、失败可重试；进入页面上报一次阅读量（防刷由后端保证）。
// 目录（TOC）滚动高亮：监听滚动，当前章节主色 + 左侧竖线。
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import CommentList from '@/modules/engagement/components/CommentList.vue'
import FeedbackDialog from '@/modules/engagement/components/FeedbackDialog.vue'
import LikeButton from '@/modules/engagement/components/LikeButton.vue'
import KnowledgeQaDialog from '@/modules/chat/components/KnowledgeQaDialog.vue'
import { fetchKnowledge, reportView } from '@/modules/publishing/api/public'
import { extractToc, renderMarkdown } from '@/modules/publishing/utils/markdown'

import type { KnowledgeDetail } from '@/modules/publishing/api/public'
import type { TocItem } from '@/modules/publishing/utils/markdown'

const route = useRoute()

const knowledge = ref<KnowledgeDetail | null>(null)
const loading = ref(true)
const loadError = ref(false)
const notFound = ref(false)
const commentCount = ref(0)

const knowledgeId = computed(() => String(route.params.id))
const toc = computed<TocItem[]>(() => (knowledge.value ? extractToc(knowledge.value.content) : []))
const renderedHtml = computed(() =>
  knowledge.value ? renderMarkdown(knowledge.value.content) : '',
)
const updatedAt = computed(() => (knowledge.value ? formatDate(knowledge.value.updatedAt) : ''))

// D02 知识级问答与 F-1001 读者纠错弹窗
const showQa = ref(false)
const showFeedback = ref(false)

// 目录滚动高亮：当前阅读章节 anchor
const activeAnchor = ref('')

function formatDate(iso: string): string {
  return iso.slice(0, 10)
}

/** 滚动监听：取视口内最靠上的标题作为当前章节。 */
function onScroll(): void {
  const anchors = toc.value.map((item) => item.anchor)
  let current = ''
  for (const anchor of anchors) {
    const el = document.getElementById(anchor)
    if (el && el.getBoundingClientRect().top <= 96) {
      current = anchor
    }
  }
  activeAnchor.value = current
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  notFound.value = false
  try {
    knowledge.value = await fetchKnowledge(knowledgeId.value)
    commentCount.value = knowledge.value.commentCount
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
    reportView(knowledgeId.value).catch(() => undefined)
    // 正文渲染完成后挂滚动监听（TOC 高亮）
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <main class="detail">
    <div v-if="loading" class="detail__state">
      <div class="detail__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="notFound" class="detail__state">
      <h1 class="detail__state-title">知识不可访问</h1>
      <p class="detail__state-text">知识不存在、已下架或未公开（F-0307）。</p>
      <RouterLink class="detail__back" to="/">返回首页</RouterLink>
    </div>
    <div v-else-if="loadError || !knowledge" class="detail__state">
      <p class="detail__state-text">知识加载失败</p>
      <el-button type="primary" plain @click="load">重试</el-button>
    </div>
    <div v-else class="detail__layout">
      <aside v-if="toc.length > 0" class="detail__toc">
        <h2 class="detail__toc-title">目录</h2>
        <button
          v-for="item in toc"
          :key="item.anchor"
          type="button"
          class="detail__toc-item"
          :class="[
            `detail__toc-item--${item.level}`,
            { 'detail__toc-item--active': item.anchor === activeAnchor },
          ]"
          @click="scrollToAnchor(item.anchor)"
        >
          {{ item.text }}
        </button>
      </aside>

      <article class="detail__knowledge">
        <header class="detail__header">
          <h1 class="detail__title">{{ knowledge.title }}</h1>
          <div class="detail__meta">
            <span>{{ knowledge.authorName }}</span>
            <span>发布于 {{ formatDate(knowledge.publishedAt) }}</span>
            <span v-if="updatedAt !== formatDate(knowledge.publishedAt)"
              >更新于 {{ updatedAt }}</span
            >
            <span>{{ knowledge.readMinutes }} 分钟阅读</span>
            <span>{{ knowledge.viewCount }} 阅读</span>
          </div>
          <div class="detail__tags">
            <RouterLink
              v-for="tag in knowledge.tags"
              :key="tag"
              :to="`/search?tag=${encodeURIComponent(tag)}`"
            >
              <el-tag effect="plain" size="small"># {{ tag }}</el-tag>
            </RouterLink>
          </div>
        </header>

        <div class="markdown-body" v-html="renderedHtml" />

        <div class="detail__actions">
          <LikeButton
            :knowledge-id="knowledge.id"
            :initial="knowledge.liked"
            :count="knowledge.likeCount"
            @update:count="knowledge.likeCount = $event"
          />
          <el-button plain @click="showQa = true">问「小光」</el-button>
          <el-button plain @click="showFeedback = true">纠错反馈</el-button>
          <span class="detail__actions-hint">登录后可点赞与评论</span>
        </div>
      </article>
    </div>

    <div v-if="knowledge && !loading && !loadError && !notFound" class="detail__comments">
      <CommentList :knowledge-id="knowledge.id" @update:count="commentCount = $event" />
    </div>

    <KnowledgeQaDialog
      v-if="showQa && knowledge"
      :knowledge-id="knowledge.id"
      :knowledge-title="knowledge.title"
      :kb-id="knowledge.kbId"
      :kb-name="knowledge.kbName"
      @close="showQa = false"
    />
    <FeedbackDialog
      v-if="showFeedback && knowledge"
      :knowledge-id="knowledge.id"
      @close="showFeedback = false"
    />
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

.detail__back {
  display: inline-block;
  margin-top: var(--xl-space-4);
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  text-decoration: none;
}

.detail__back:hover {
  background: var(--xl-color-primary-hover);
}

.detail__layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 760px);
  gap: var(--xl-space-6);
  justify-content: center;
  align-items: start;
}

.detail__toc {
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 96px);
  overflow-y: auto;
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.detail__toc-title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.detail__toc-item {
  display: block;
  width: 100%;
  padding: 5px 8px;
  border: none;
  border-radius: var(--xl-radius-sm);
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}

.detail__toc-item:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.detail__toc-item--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--xl-color-primary);
}

.detail__toc-item--3 {
  padding-left: var(--xl-space-4);
}

.detail__toc-item--4 {
  padding-left: var(--xl-space-6);
}

.detail__knowledge {
  min-width: 0;
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
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

.detail__tags a {
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

.detail__comments {
  max-width: 760px;
  margin: var(--xl-space-6) auto 0;
}

/* Markdown 正文样式（B02）：与设计 token 对齐，代码块等保持可读 */
.markdown-body {
  margin-top: var(--xl-space-4);
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
  box-shadow: var(--xl-shadow-sm);
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

  .detail__comments {
    margin-top: var(--xl-space-4);
  }
}
</style>
