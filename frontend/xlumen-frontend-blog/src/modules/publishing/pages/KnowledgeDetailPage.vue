<script setup lang="ts">
// 知识详情（B02）：标题/作者/时间/阅读时间/标签 + AI 摘要 +
// 目录导航 + Markdown 正文 + 赞/踩/收藏/评论。
// 关键状态：加载骨架、404 不可访问解释、失败可重试；进入页面上报一次阅读量（防刷由后端保证）。
// 目录（TOC）滚动高亮：监听滚动，当前章节主色 + 左侧竖线。
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RouterLink, useRoute } from 'vue-router'
import { ArrowDown, Headset } from '@element-plus/icons-vue'

import CommentList from '@/modules/engagement/components/CommentList.vue'
import FavoriteButton from '@/modules/engagement/components/FavoriteButton.vue'
import FeedbackDialog from '@/modules/engagement/components/FeedbackDialog.vue'
import ReactionBar from '@/modules/engagement/components/ReactionBar.vue'
import KnowledgeQaDialog from '@/modules/chat/components/KnowledgeQaDialog.vue'
import { assistAction, explainTerm } from '@/modules/ai/api/assist'
import { useSessionStore } from '@/stores/session'
import { fetchKnowledge, fetchRelatedKnowledge, reportView } from '@/modules/publishing/api/public'
import { extractToc, renderMarkdown } from '@/modules/publishing/utils/markdown'

import type { AssistAction, TermExplainResult } from '@/modules/ai/api/assist'
import type { KnowledgeDetail, RelatedKnowledge } from '@/modules/publishing/api/public'
import type { TocItem } from '@/modules/publishing/utils/markdown'

const route = useRoute()
const session = useSessionStore()

const knowledge = ref<KnowledgeDetail | null>(null)
const loading = ref(true)
const loadError = ref(false)
const notFound = ref(false)
const commentCount = ref(0)

const knowledgeId = computed(() => String(route.params.id))
const toc = computed<TocItem[]>(() => (knowledge.value ? extractToc(knowledge.value.content) : []))
// 正文若以与标题相同的一级标题开头，去掉该行，避免页头标题重复渲染
function stripLeadingTitle(source: string): string {
  const match = /^(#\s+.+)\r?\n?/.exec(source.trimStart())
  if (match && match[1] && match[1].replace(/^#\s+/, '').trim() === knowledge.value?.title.trim()) {
    return source.trimStart().slice(match[0].length)
  }
  return source
}

const renderedHtml = computed(() =>
  knowledge.value ? renderMarkdown(stripLeadingTitle(knowledge.value.content)) : '',
)
const updatedAt = computed(() => (knowledge.value ? formatDate(knowledge.value.updatedAt) : ''))

// 相关推荐：正文下方列表，至多 5 条；无数据不渲染区块。
const related = ref<RelatedKnowledge[]>([])

async function loadRelated(): Promise<void> {
  try {
    related.value = (await fetchRelatedKnowledge(knowledgeId.value)).slice(0, 5)
  } catch {
    related.value = []
  }
}

// AI 摘要与导读：aiSummary 兼容两种形态——纯文本旧格式直接展示；
// 新格式为 JSON {"summary":..., "guide":[...]}，展示 summary 文本并渲染 AI 导读要点卡。
interface AiSummaryInfo {
  summary: string
  guide: string[]
}

function parseAiSummary(raw: string): AiSummaryInfo | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null) return null
  const candidate = parsed as { summary?: unknown; guide?: unknown }
  if (typeof candidate.summary !== 'string') return null
  const guide = Array.isArray(candidate.guide)
    ? candidate.guide.filter(
        (point): point is string => typeof point === 'string' && point.trim().length > 0,
      )
    : []
  return { summary: candidate.summary, guide }
}

const aiSummaryInfo = computed<AiSummaryInfo | null>(() =>
  knowledge.value?.aiSummary ? parseAiSummary(knowledge.value.aiSummary) : null,
)
/** 摘要卡片文案：JSON 格式取 summary 字段，旧格式原样展示。 */
const summaryText = computed(() => aiSummaryInfo.value?.summary ?? knowledge.value?.aiSummary ?? '')
/** AI 导读要点；仅 JSON 且 guide 非空时渲染导读卡。 */
const aiGuide = computed<string[]>(() => aiSummaryInfo.value?.guide ?? [])
const guideOpen = ref(true)

// D02 知识级问答与 读者纠错弹窗
const showQa = ref(false)
const showFeedback = ref(false)

// 代码块 AI 解读（F-0607）：正文渲染后动态给每个 <pre><code> 包一个右上角操作条，
// 不在 markdown 内插入 HTML；渲染结果在弹窗内展示 Markdown 并支持复制。
const contentEl = ref<HTMLElement | null>(null)

interface CodeAiAction {
  action: AssistAction
  label: string
  dialogTitle: string
}

const CODE_AI_ACTIONS: ReadonlyArray<CodeAiAction> = [
  { action: 'code_explain', label: '解释', dialogTitle: 'AI 代码解释' },
  { action: 'code_bug', label: '找 Bug', dialogTitle: 'AI 找 Bug' },
  { action: 'code_test', label: '生成测试', dialogTitle: 'AI 生成测试' },
]

/** 操作条前置的 Sparkles 标识（静态 SVG，与 AiSparkles 同源图形）。 */
const SPARKLES_SVG =
  '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9.94 15.5a2 2 0 0 0-1.44-1.44l-6.13-1.58a.5.5 0 0 1 0-.96l6.13-1.58A2 2 0 0 0 9.94 8.5l1.58-6.13a.5.5 0 0 1 .96 0l1.58 6.13a2 2 0 0 0 1.44 1.44l6.13 1.58a.5.5 0 0 1 0 .96l-6.13 1.58a2 2 0 0 0-1.44 1.44l-1.58 6.13a.5.5 0 0 1-.96 0z"></path><path d="M20 3v4"></path><path d="M22 5h-4"></path><path d="M4 17v2"></path><path d="M5 18H3"></path></svg>'

/** 正在进行的代码动作（key 形如 “代码块序号:动作”），防止同按钮重复提交。 */
const busyCodeActions = new Set<string>()

/** 代码解读结果弹窗。 */
const codeDialog = ref<{ title: string; result: string } | null>(null)
const codeDialogVisible = computed({
  get: () => codeDialog.value !== null,
  set: (visible: boolean) => {
    if (!visible) codeDialog.value = null
  },
})

let codeBlockSeq = 0

/** 给代码块包上 AI 操作条：渲染后调用，VNode 更新后以 dataset/已有子元素去重。 */
function enhanceCodeBlocks(): void {
  const root = contentEl.value
  if (!root) return
  const codeBlocks = root.querySelectorAll<HTMLElement>('pre code')
  codeBlocks.forEach((code) => {
    const pre = (code.closest('pre') ?? code.parentElement) as HTMLElement | null
    if (!pre || pre.querySelector('.code-ai-bar')) return
    pre.classList.add('detail__code')
    const seq = codeBlockSeq++
    const bar = document.createElement('div')
    bar.className = 'code-ai-bar'
    bar.setAttribute('aria-label', 'AI 代码解读')
    const mark = document.createElement('span')
    mark.className = 'code-ai-bar__mark'
    mark.innerHTML = SPARKLES_SVG
    bar.appendChild(mark)
    CODE_AI_ACTIONS.forEach((item) => {
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'code-ai-bar__btn'
      button.textContent = item.label
      button.addEventListener('click', () =>
        handleCodeAction(item, code, button, `${seq}:${item.action}`),
      )
      bar.appendChild(button)
    })
    pre.appendChild(bar)
  })
}

/** 代码动作：未登录提示登录；成功后弹窗展示 AI 返回的 Markdown 文本。 */
async function handleCodeAction(
  item: CodeAiAction,
  code: HTMLElement,
  button: HTMLButtonElement,
  busyKey: string,
): Promise<void> {
  if (!session.loggedIn) {
    ElMessage.warning('请先登录后使用 AI 代码解读')
    return
  }
  if (busyCodeActions.has(busyKey)) return
  const source = code.textContent ?? ''
  if (!source.trim()) {
    ElMessage.warning('该代码块为空，暂无可解读内容')
    return
  }
  busyCodeActions.add(busyKey)
  button.disabled = true
  button.classList.add('is-loading')
  try {
    const result = await assistAction({ action: item.action, content: source })
    codeDialog.value = { title: item.dialogTitle, result }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 代码解读失败，请稍后重试')
  } finally {
    button.classList.remove('is-loading')
    button.disabled = false
    busyCodeActions.delete(busyKey)
  }
}

/** 复制 AI 解读结果。 */
async function copyCodeResult(): Promise<void> {
  if (!codeDialog.value) return
  try {
    await navigator.clipboard.writeText(codeDialog.value.result)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

// 图片 AI 讲解（需登录）：正文渲染后给每张 <img> 右下角挂「AI 讲解」按钮，
// 绝对化图片地址后交给 assist 图片讲解动作，结果以 Markdown 弹窗展示并可复制。
interface ImageExplainState {
  imageUrl: string
  result: string
  loading: boolean
}

const imageExplainState = ref<ImageExplainState | null>(null)
const imageExplainVisible = computed({
  get: () => imageExplainState.value !== null,
  set: (visible: boolean) => {
    if (!visible) imageExplainState.value = null
  },
})

/** 图片地址绝对化：http(s) 原样共享；相对路径补 location.origin；data/blob 等内嵌资源不支持。 */
function absolutizeImageUrl(src: string): string {
  const trimmed = src.trim()
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  if (trimmed.startsWith('//')) return `${window.location.protocol}${trimmed}`
  if (/^(data|blob|javascript|mailto):/i.test(trimmed)) return ''
  return trimmed.startsWith('/')
    ? `${window.location.origin}${trimmed}`
    : `${window.location.origin}/${trimmed}`
}

/** 给正文图片包一层行内容器并挂 AI 讲解按钮（以 span 包裹，div 在 <p> 内会被浏览器拆出段落）。 */
function enhanceImages(): void {
  const root = contentEl.value
  if (!root) return
  const images = root.querySelectorAll<HTMLImageElement>('img:not([data-ai-explain-done])')
  images.forEach((img) => {
    img.setAttribute('data-ai-explain-done', '1')
    const wrap = document.createElement('span')
    wrap.className = 'detail__img-box'
    img.parentElement?.insertBefore(wrap, img)
    wrap.appendChild(img)
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'detail__img-btn'
    button.textContent = 'AI 讲解'
    button.setAttribute('aria-label', 'AI 图片讲解')
    button.addEventListener('click', () => handleImageExplain(img, button))
    wrap.appendChild(button)
  })
}

async function handleImageExplain(img: HTMLImageElement, button: HTMLButtonElement): Promise<void> {
  if (!session.loggedIn) {
    ElMessage.warning('请先登录后使用 AI 图片讲解')
    return
  }
  if (imageExplainState.value) return
  const rawSrc = img.getAttribute('src') ?? ''
  if (!rawSrc.trim()) {
    ElMessage.warning('该图片没有地址，暂无法讲解')
    return
  }
  const imageUrl = absolutizeImageUrl(rawSrc)
  if (!imageUrl) {
    ElMessage.warning('该图片为本地内嵌资源，暂不支持讲解')
    return
  }
  button.disabled = true
  button.classList.add('is-loading')
  imageExplainState.value = { imageUrl, result: '', loading: true }
  try {
    const result = await assistAction({ action: 'image_explain', imageUrl })
    if (imageExplainState.value) {
      imageExplainState.value.result = result
      imageExplainState.value.loading = false
    }
  } catch (error) {
    imageExplainState.value = null
    ElMessage.error(error instanceof Error ? error.message : 'AI 图片讲解失败，请稍后重试')
  } finally {
    button.classList.remove('is-loading')
    button.disabled = false
  }
}

/** 复制图片讲解结果。 */
async function copyImageResult(): Promise<void> {
  if (!imageExplainState.value?.result) return
  try {
    await navigator.clipboard.writeText(imageExplainState.value.result)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

// 术语悬浮解释（公开接口无需登录）：正文选中 ≤30 字后浮出「问小光解释」气泡，
// 点击调用 term-explain，小弹窗展示解释文案与缓存标记；不与正文既有鼠标行为冲突。
const termHover = reactive({ visible: false, term: '', x: 0, y: 0 })
const termPopup = reactive({ visible: false, term: '', x: 0, y: 0 })
const termBubbleEl = ref<HTMLElement | null>(null)
const termPopupEl = ref<HTMLElement | null>(null)
const termResult = ref<TermExplainResult | null>(null)
const termExplaining = ref(false)

function hideTermEnhance(): void {
  termHover.visible = false
  termPopup.visible = false
}

function onContentMouseUp(event: MouseEvent): void {
  const selection = window.getSelection()
  const anchorNode = selection?.anchorNode
  // 选区须落在正文容器内，避免复制工具栏/评论区等处的选区误弹
  if (!selection || !anchorNode || !contentEl.value?.contains(anchorNode)) {
    hideTermEnhance()
    return
  }
  const text = selection.toString().trim()
  if (!text || text.length > 30 || termExplaining.value) {
    hideTermEnhance()
    return
  }
  termResult.value = null
  termPopup.visible = false
  termHover.term = text
  termHover.x = Math.min(event.clientX, window.innerWidth - 150)
  termHover.y = Math.min(event.clientY, window.innerHeight - 60)
  termHover.visible = true
}

/** 点击气泡：气泡让位给小弹窗，解释进行中显示 loading，结束后展示文案。 */
async function askTermExplain(): Promise<void> {
  const term = termHover.term
  if (!term || termExplaining.value) return
  termResult.value = null
  termPopup.term = term
  termPopup.x = termHover.x
  termPopup.y = Math.min(termHover.y + 36, window.innerHeight - 160)
  termPopup.visible = true
  termHover.visible = false
  termExplaining.value = true
  try {
    termResult.value = await explainTerm(term)
  } catch (error) {
    termPopup.visible = false
    ElMessage.error(error instanceof Error ? error.message : '解释失败，请稍后重试')
  } finally {
    termExplaining.value = false
  }
}

function onGlobalMouseDown(event: MouseEvent): void {
  const target = event.target
  if (!target || !(target instanceof Node)) return
  if (termBubbleEl.value?.contains(target) || termPopupEl.value?.contains(target)) return
  hideTermEnhance()
}

function onGlobalKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') hideTermEnhance()
}

// 听知识（访客/登录均可用）：标题区按钮开关 <audio>，直接走 /api/v1 代理；
// 加载失败（501/网络错误等）提示语音功能暂不可用。
const playerOpen = ref(false)
const speechError = ref(false)
const audioEl = ref<HTMLAudioElement | null>(null)

const speechUrl = computed(() =>
  knowledge.value ? `/api/v1/public/knowledge/${knowledge.value.id}/speech` : '',
)

function togglePlayer(): void {
  playerOpen.value = !playerOpen.value
  speechError.value = false
  if (!playerOpen.value) {
    audioEl.value?.pause()
  }
}

function onSpeechError(): void {
  speechError.value = true
  ElMessage.warning('语音功能暂不可用')
}

// 目录滚动高亮：当前阅读章节 anchor
const activeAnchor = ref('')

function formatDate(iso: string | null): string {
  return iso ? iso.slice(0, 10) : ''
}

/** 滚动监听：取视口内最靠上的标题作为当前章节；滚动同时收起悬浮术语气泡。 */
function onScroll(): void {
  hideTermEnhance()
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

/** 赞/踩计数同步：ReactionBar 以服务端结果校正后回传。 */
function onCountsChange(counts: { likeCount: number; dislikeCount: number }): void {
  if (!knowledge.value) return
  knowledge.value.likeCount = counts.likeCount
  knowledge.value.dislikeCount = counts.dislikeCount
}

/** 收藏状态同步。 */
function onFavoriteChange(state: { favorited: boolean; count: number }): void {
  if (!knowledge.value) return
  knowledge.value.favorited = state.favorited
  knowledge.value.favoriteCount = state.count
}

onMounted(async () => {
  window.addEventListener('mousedown', onGlobalMouseDown, true)
  window.addEventListener('keydown', onGlobalKeydown)
  await load()
  if (!notFound.value && !loadError.value) {
    // 阅读量上报：失败不影响阅读
    reportView(knowledgeId.value).catch(() => undefined)
    // 相关推荐：失败静默，无数据不渲染区块
    void loadRelated()
    // 正文渲染完成后挂滚动监听（TOC 高亮）
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('mousedown', onGlobalMouseDown, true)
  window.removeEventListener('keydown', onGlobalKeydown)
})

// 正文 v-html 更新后（含首次加载）为代码块补包 AI 操作条、为图片挂 AI 讲解按钮；flush post 确保 DOM 已渲染
watch(renderedHtml, () => {
  if (!knowledge.value) return
  void nextTick(() => {
    enhanceCodeBlocks()
    enhanceImages()
  })
})
</script>

<template>
  <main class="detail">
    <div v-if="loading" class="detail__state">
      <div class="detail__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="notFound" class="detail__state">
      <h1 class="detail__state-title">知识不可访问</h1>
      <p class="detail__state-text">知识不存在、已下架或未公开。</p>
      <RouterLink class="detail__back" to="/">返回首页</RouterLink>
    </div>
    <div v-else-if="loadError || !knowledge" class="detail__state">
      <p class="detail__state-text">知识加载失败</p>
      <el-button type="primary" plain @click="load">重试</el-button>
    </div>
    <div v-else class="detail__layout" :class="{ 'detail__layout--single': toc.length === 0 }">
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
            <el-button class="detail__listen" size="small" round @click="togglePlayer">
              <el-icon class="detail__listen-icon"><Headset /></el-icon>
              {{ playerOpen ? '收起语音' : '听知识' }}
            </el-button>
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

        <!-- 听知识：标题区开关音频（公开接口，访客/登录可用） -->
        <div v-if="playerOpen" class="detail__player">
          <audio
            ref="audioEl"
            controls
            preload="metadata"
            :src="speechUrl"
            @error="onSpeechError"
          />
          <p v-if="speechError" class="detail__player-error">语音服务暂不可用，可稍后重试。</p>
        </div>

        <!-- AI 摘要：有值才渲染，浅色卡片，不参与 TOC；JSON 新格式只展示 summary 文案 -->
        <div v-if="knowledge.aiSummary" class="detail__summary">
          <el-tag class="detail__summary-tag" size="small" effect="plain">AI 摘要</el-tag>
          <p class="detail__summary-text">{{ summaryText }}</p>
        </div>

        <!-- AI 导读：aiSummary 为 JSON 且含 guide 数组时渲染折叠卡，默认展开 -->
        <div v-if="aiGuide.length > 0" class="detail__guide">
          <div
            class="detail__guide-head"
            role="button"
            tabindex="0"
            @click="guideOpen = !guideOpen"
            @keydown.enter="guideOpen = !guideOpen"
          >
            <span class="detail__guide-icon" aria-hidden="true" v-html="SPARKLES_SVG" />
            <span class="detail__guide-title">AI 导读</span>
            <el-icon class="detail__guide-arrow" :class="{ 'detail__guide-arrow--open': guideOpen }"
              ><ArrowDown
            /></el-icon>
          </div>
          <div v-if="guideOpen" class="detail__guide-body">
            <p class="detail__guide-tldr">
              <span class="detail__guide-label">TL;DR</span>
              <span class="detail__guide-summary">{{ summaryText }}</span>
            </p>
            <ul class="detail__guide-points">
              <li v-for="(point, index) in aiGuide" :key="index">{{ point }}</li>
            </ul>
          </div>
        </div>

        <div
          ref="contentEl"
          class="markdown-body"
          @mouseup="onContentMouseUp"
          v-html="renderedHtml"
        />

        <div class="detail__actions">
          <ReactionBar
            :knowledge-id="knowledge.id"
            :initial-reaction="knowledge.liked ? 'LIKE' : null"
            :like-count="knowledge.likeCount"
            :dislike-count="knowledge.dislikeCount"
            @update:counts="onCountsChange"
          />
          <FavoriteButton
            :knowledge-id="knowledge.id"
            :initial="knowledge.favorited"
            :count="knowledge.favoriteCount"
            @update:state="onFavoriteChange"
          />
          <el-button plain @click="showQa = true">问「小光」</el-button>
          <el-button plain @click="showFeedback = true">纠错反馈</el-button>
          <span v-if="!session.loggedIn" class="detail__actions-hint"
            >登录后可点赞、收藏与评论</span
          >
        </div>
      </article>
    </div>

    <!-- 相关推荐：正文下方，无数据不渲染区块 -->
    <section v-if="related.length > 0" class="detail__related">
      <h2 class="detail__related-title">相关推荐</h2>
      <ul class="detail__related-list">
        <li v-for="item in related" :key="item.id" class="detail__related-item">
          <RouterLink class="detail__related-link" :to="`/knowledge/${item.id}`">
            <span class="detail__related-name">{{ item.title }}</span>
            <span v-if="item.kbName" class="detail__related-kb">{{ item.kbName }}</span>
            <span v-if="formatDate(item.publishedAt)" class="detail__related-date">{{
              formatDate(item.publishedAt)
            }}</span>
          </RouterLink>
        </li>
      </ul>
    </section>

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

    <el-dialog
      v-model="codeDialogVisible"
      :title="codeDialog?.title ?? 'AI 代码解读'"
      width="min(720px, 92vw)"
      append-to-body
    >
      <div v-if="codeDialog" class="detail__code-result">
        <div
          class="markdown-body detail__code-result-body"
          v-html="renderMarkdown(codeDialog.result)"
        />
        <div class="detail__code-result-actions">
          <el-button size="small" type="primary" plain @click="copyCodeResult">复制</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 图片 AI 讲解结果弹窗：loading 中占位，成功后 Markdown 渲染可复制 -->
    <el-dialog
      v-model="imageExplainVisible"
      title="AI 图片讲解"
      width="min(720px, 92vw)"
      append-to-body
    >
      <div v-if="imageExplainState" class="detail__code-result">
        <p v-if="imageExplainState.loading" class="detail__img-dialog-loading" role="status">
          小光正在解析图片内容…
        </p>
        <div
          v-else
          class="markdown-body detail__code-result-body"
          v-html="renderMarkdown(imageExplainState.result)"
        />
        <div v-if="!imageExplainState.loading" class="detail__code-result-actions">
          <el-button size="small" type="primary" plain @click="copyImageResult">复制</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 术语悬浮解释：正文选中 ≤30 字后浮出气泡，点击弹出 1-2 句解释 -->
    <div
      v-if="termHover.visible"
      ref="termBubbleEl"
      class="detail__term-bubble"
      :style="{ left: `${termHover.x}px`, top: `${termHover.y}px` }"
    >
      <button type="button" class="detail__term-bubble-btn" @click="askTermExplain">
        问小光解释
      </button>
    </div>
    <div
      v-if="termPopup.visible"
      ref="termPopupEl"
      class="detail__term-popup"
      :style="{ left: `${termPopup.x}px`, top: `${termPopup.y}px` }"
    >
      <div class="detail__term-popup-head">
        <span class="detail__term-popup-word">「{{ termPopup.term }}」</span>
        <el-tag v-if="termResult?.fromCache" size="small" type="info" effect="plain">缓存</el-tag>
      </div>
      <p v-if="termExplaining" class="detail__term-popup-loading" role="status">小光正在解释…</p>
      <p v-else-if="termResult" class="detail__term-popup-text">{{ termResult.explanation }}</p>
    </div>
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

/* 目录为空时目录栏不渲染，必须退回单栏，否则正文被塞进 200px 的目录列 */
.detail__layout--single {
  grid-template-columns: minmax(0, 760px);
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

/* AI 摘要区块：header 与正文之间，浅色卡片（AI 色 token 化） */
.detail__summary {
  display: flex;
  gap: var(--xl-space-3);
  align-items: flex-start;
  margin-top: var(--xl-space-4);
  padding: var(--xl-space-3) var(--xl-space-4);
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 30%, transparent);
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-color-ai) 6%, transparent);
}

.detail__summary-tag {
  flex-shrink: 0;
}

.detail__summary-text {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: break-word;
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
  position: relative;
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

/* 已包 AI 操作条的代码块：顶部预留操作条空间，避免遮挡首行代码 */
.markdown-body :deep(.detail__code) {
  padding-top: 40px;
}

/* 代码块右上角 AI 操作条（渲染后动态包一层，AI 色点缀） */
.markdown-body :deep(.code-ai-bar) {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 45%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--xl-bg-surface) 88%, transparent);
  box-shadow: var(--xl-shadow-sm);
  backdrop-filter: blur(4px);
}

.markdown-body :deep(.code-ai-bar__mark) {
  display: inline-flex;
  align-items: center;
  padding: 4px 2px 4px 6px;
  color: var(--xl-color-ai);
}

.markdown-body :deep(.code-ai-bar__btn) {
  padding: 3px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--xl-color-ai);
  font-size: 12px;
  cursor: pointer;
}

.markdown-body :deep(.code-ai-bar__btn:hover) {
  background: color-mix(in srgb, var(--xl-color-ai) 12%, transparent);
}

.markdown-body :deep(.code-ai-bar__btn:disabled) {
  cursor: not-allowed;
  opacity: 0.7;
}

/* 加载态：按钮前置旋转圈 */
.markdown-body :deep(.code-ai-bar__btn.is-loading) {
  pointer-events: none;
  opacity: 0.85;
}

.markdown-body :deep(.code-ai-bar__btn.is-loading::before) {
  content: '';
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 4px;
  vertical-align: -1px;
  border: 2px solid color-mix(in srgb, var(--xl-color-ai) 30%, transparent);
  border-top-color: var(--xl-color-ai);
  border-radius: 50%;
  animation: xl-code-ai-spin 0.8s linear infinite;
}

@keyframes xl-code-ai-spin {
  to {
    transform: rotate(360deg);
  }
}

/* AI 解读结果弹窗内容 */
.detail__code-result-body {
  margin: 0;
}

.detail__code-result-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--xl-space-3);
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

/* 听知识：标题区按钮 + 音频条 */
.detail__listen {
  margin-left: auto;
  border-color: color-mix(in srgb, var(--xl-color-ai) 45%, transparent);
  color: var(--xl-color-ai);
}

.detail__listen:hover {
  background: color-mix(in srgb, var(--xl-color-ai) 8%, transparent);
}

.detail__listen-icon {
  margin-right: 4px;
}

.detail__player {
  margin-top: var(--xl-space-3);
  padding: var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-secondary);
}

.detail__player audio {
  width: 100%;
  height: 36px;
}

.detail__player-error {
  margin: var(--xl-space-2) 0 0;
  color: var(--xl-text-muted);
  font-size: 12px;
}

/* AI 导读：AI 色左侧强调线 + 火星图标，折叠卡默认展开 */
.detail__guide {
  margin-top: var(--xl-space-4);
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 30%, transparent);
  border-left: 3px solid var(--xl-color-ai);
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-color-ai) 5%, transparent);
}

.detail__guide-head {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-3) var(--xl-space-4);
  cursor: pointer;
  user-select: none;
}

.detail__guide-head:hover {
  background: color-mix(in srgb, var(--xl-color-ai) 7%, transparent);
}

.detail__guide-icon {
  display: inline-flex;
  align-items: center;
  color: var(--xl-color-ai);
}

.detail__guide-icon :deep(svg) {
  width: 14px;
  height: 14px;
}

.detail__guide-title {
  color: var(--xl-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.detail__guide-arrow {
  margin-left: auto;
  color: var(--xl-text-muted);
  font-size: 13px;
  transition: transform var(--xl-transition);
}

.detail__guide-arrow--open {
  transform: rotate(180deg);
}

.detail__guide-body {
  padding: 0 var(--xl-space-4) var(--xl-space-4) var(--xl-space-4);
}

.detail__guide-tldr {
  display: flex;
  gap: var(--xl-space-2);
  align-items: flex-start;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
}

.detail__guide-label {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-ai) 15%, transparent);
  color: var(--xl-color-ai);
  font-size: 12px;
  font-weight: 600;
}

.detail__guide-summary {
  color: var(--xl-text-secondary);
  overflow-wrap: break-word;
}

.detail__guide-points {
  margin: var(--xl-space-2) 0 0;
  padding-left: 1.5em;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}

/* 相关推荐 */
.detail__related {
  max-width: 760px;
  margin: var(--xl-space-6) auto 0;
}

.detail__related-title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.detail__related-list {
  list-style: none;
  margin: 0;
  padding: var(--xl-space-2);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.detail__related-item {
  border-bottom: 1px solid var(--xl-border);
}

.detail__related-item:last-child {
  border-bottom: none;
}

.detail__related-link {
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  padding: var(--xl-space-2) var(--xl-space-3);
  border-radius: var(--xl-radius-sm);
  color: var(--xl-text-primary);
  font-size: 14px;
  text-decoration: none;
}

.detail__related-link:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.detail__related-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail__related-kb {
  flex-shrink: 0;
  color: var(--xl-color-primary);
  font-size: 12px;
}

.detail__related-date {
  flex-shrink: 0;
  color: var(--xl-text-muted);
  font-size: 12px;
}

/* 术语悬浮解释：气泡 + 解释小弹窗 */
.detail__term-bubble {
  position: fixed;
  z-index: 3000;
}

.detail__term-bubble-btn {
  padding: 6px 14px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 45%, transparent);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-color-ai);
  font-size: 13px;
  font-weight: 600;
  box-shadow: var(--xl-shadow-md);
  cursor: pointer;
}

.detail__term-bubble-btn:hover {
  background: color-mix(in srgb, var(--xl-color-ai) 10%, transparent);
}

.detail__term-popup {
  position: fixed;
  z-index: 3000;
  width: 300px;
  padding: var(--xl-space-3) var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-lg);
}

.detail__term-popup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-2);
}

.detail__term-popup-word {
  color: var(--xl-color-ai);
  font-size: 13px;
  font-weight: 600;
  overflow-wrap: break-word;
}

.detail__term-popup-loading {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: 13px;
}

.detail__term-popup-text {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: break-word;
}

/* 图片 AI 讲解：图片右下角悬浮按钮（AI 色，hover 增强不透明度） */
.markdown-body :deep(.detail__img-box) {
  position: relative;
  display: inline-block;
  max-width: 100%;
}

.markdown-body :deep(.detail__img-box img) {
  display: block;
  max-width: 100%;
  border-radius: var(--xl-radius-card);
}

.markdown-body :deep(.detail__img-btn) {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 45%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-bg-surface) 88%, transparent);
  color: var(--xl-color-ai);
  font-size: 12px;
  font-weight: 600;
  box-shadow: var(--xl-shadow-sm);
  cursor: pointer;
  opacity: 0.8;
  backdrop-filter: blur(4px);
}

.markdown-body :deep(.detail__img-box:hover .detail__img-btn) {
  opacity: 1;
}

.markdown-body :deep(.detail__img-btn.is-loading) {
  opacity: 0.6;
  pointer-events: none;
}

.detail__img-dialog-loading {
  margin: var(--xl-space-4) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-align: center;
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
