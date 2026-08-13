<script setup lang="ts">
// AI 写作页（B11，F-0601/F-0604）：主题/草稿/完整文章三种输入，流式打字展示生成过程，
// 完成后展示标题 + Markdown 预览，可保存为新文章（走 content createArticle）。
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createArticle } from '@/modules/content/api/article'
import { fetchWritingTask, retryWritingTask, submitWriting } from '@/modules/ai/api/writing'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import { streamSse } from '@/modules/ai/utils/sse'
import AiTaskProgress from '@/modules/ai/components/AiTaskProgress.vue'

import type { WritingRequest } from '@/modules/ai/api/writing'
import type { SseEvent } from '@/modules/ai/utils/sse'

type WriteMode = 'topic' | 'draft' | 'content'
type WritePhase = 'idle' | 'submitting' | 'streaming' | 'done' | 'error'

const MODES: ReadonlyArray<{ value: WriteMode; label: string }> = [
  { value: 'topic', label: '按主题' },
  { value: 'draft', label: '按草稿' },
  { value: 'content', label: '完整文章' },
]

const router = useRouter()

const mode = ref<WriteMode>('topic')
const topic = ref('')
const draft = ref('')
const contentTitle = ref('')
const contentBody = ref('')

const phase = ref<WritePhase>('idle')
const taskId = ref<string | null>(null)
const streamText = ref('')
const resultTitle = ref('')
const resultContent = ref('')
const errorMsg = ref('')
const saving = ref(false)
const saveMessage = ref('')

let controller: AbortController | null = null

const canSubmit = computed(() => {
  if (mode.value === 'topic') return topic.value.trim().length > 0
  if (mode.value === 'draft') return draft.value.trim().length > 0
  return contentBody.value.trim().length > 0
})

const renderedResult = computed(() => (resultContent.value ? renderMarkdown(resultContent.value) : ''))

function buildPayload(): WritingRequest {
  if (mode.value === 'topic') return { topic: topic.value.trim() }
  if (mode.value === 'draft') return { draft: draft.value.trim() }
  return {
    ...(contentTitle.value.trim() ? { title: contentTitle.value.trim() } : {}),
    content: contentBody.value.trim(),
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value || phase.value === 'submitting' || phase.value === 'streaming') return
  phase.value = 'submitting'
  errorMsg.value = ''
  saveMessage.value = ''
  try {
    const submitted = await submitWriting(buildPayload())
    taskId.value = submitted.taskId
    await startStreaming(submitted.taskId)
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : '提交失败，请稍后重试'
    phase.value = 'error'
  }
}

async function startStreaming(id: string): Promise<void> {
  phase.value = 'streaming'
  streamText.value = ''
  resultTitle.value = ''
  resultContent.value = ''
  controller = new AbortController()
  try {
    await streamSse(`/ai/tasks/${id}/events`, { method: 'GET', signal: controller.signal }, (event) => {
      handleEvent(id, event)
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    if (phase.value === 'streaming') {
      errorMsg.value = error instanceof Error ? error.message : '生成失败'
      phase.value = 'error'
    }
  } finally {
    controller = null
  }
}

function handleEvent(id: string, event: SseEvent): void {
  if (event.event === 'chunk') {
    streamText.value += event.data
    return
  }
  if (event.event === 'error') {
    errorMsg.value = event.data
    phase.value = 'error'
    controller?.abort()
    return
  }
  if (event.event === 'done') {
    void handleDone(id, event.data)
  }
}

async function handleDone(id: string, data: string): Promise<void> {
  try {
    const parsed = JSON.parse(data) as { resultJson?: string }
    if (parsed.resultJson) {
      applyResult(parsed.resultJson)
    } else {
      const task = await fetchWritingTask(id)
      if (task.resultJson) applyResult(task.resultJson)
      else resultContent.value = streamText.value
    }
  } catch {
    // 结果不可解析时回退为流式文本
    resultContent.value = streamText.value
  }
  phase.value = 'done'
  controller?.abort()
}

function applyResult(resultJson: string): void {
  try {
    const parsed = JSON.parse(resultJson) as { title?: string; content?: string }
    resultTitle.value = parsed.title ?? ''
    resultContent.value = parsed.content ?? streamText.value
  } catch {
    resultContent.value = streamText.value
  }
}

async function handleRetry(): Promise<void> {
  if (!taskId.value) return
  phase.value = 'submitting'
  errorMsg.value = ''
  try {
    await retryWritingTask(taskId.value)
    await startStreaming(taskId.value)
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : '重试失败'
    phase.value = 'error'
  }
}

function reset(): void {
  controller?.abort()
  controller = null
  phase.value = 'idle'
  taskId.value = null
  streamText.value = ''
  resultTitle.value = ''
  resultContent.value = ''
  errorMsg.value = ''
  saveMessage.value = ''
}

async function saveAsArticle(): Promise<void> {
  const title = resultTitle.value.trim()
  const body = resultContent.value.trim()
  if (!title || !body) {
    saveMessage.value = '生成内容不完整，请重新生成后再保存'
    return
  }
  saving.value = true
  saveMessage.value = ''
  try {
    const created = await createArticle({ title, content: body, category: '', tags: [], visibility: 1 })
    await router.push({ name: 'article-edit', params: { id: created.id } })
  } catch (error) {
    saveMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<template>
  <main class="ai-write">
    <header class="ai-write__header">
      <h1 class="ai-write__title">AI 写作</h1>
      <p class="ai-write__intro">「小光」根据主题、草稿或完整文章，流式生成一篇可直接发布的 Markdown 文章。</p>
    </header>

    <section v-if="phase === 'idle'" class="ai-write__form">
      <div class="ai-write__tabs" role="tablist" aria-label="输入模式">
        <button
          v-for="item in MODES"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="mode === item.value"
          class="ai-write__tab"
          :class="{ 'ai-write__tab--active': mode === item.value }"
          @click="mode = item.value"
        >
          {{ item.label }}
        </button>
      </div>

      <label v-if="mode === 'topic'" class="ai-write__field">
        <span class="ai-write__field-label">写作主题</span>
        <textarea v-model="topic" class="ai-write__textarea" rows="4" placeholder="例如：Spring Boot 自动配置原理与实践" />
      </label>
      <label v-else-if="mode === 'draft'" class="ai-write__field">
        <span class="ai-write__field-label">草稿 / 提纲</span>
        <textarea v-model="draft" class="ai-write__textarea" rows="8" placeholder="粘贴你的草稿或提纲，小光会帮你扩写润色…" />
      </label>
      <template v-else>
        <label class="ai-write__field">
          <span class="ai-write__field-label">文章标题（可选）</span>
          <input v-model="contentTitle" class="ai-write__input" type="text" placeholder="例如：深入理解 JVM 内存模型" />
        </label>
        <label class="ai-write__field">
          <span class="ai-write__field-label">完整文章</span>
          <textarea v-model="contentBody" class="ai-write__textarea" rows="12" placeholder="粘贴完整文章，小光会帮你润色改写、优化结构…" />
        </label>
      </template>

      <button type="button" class="ai-write__submit" :disabled="!canSubmit" @click="submit">开始写作</button>
    </section>

    <div v-if="phase === 'submitting' || phase === 'streaming'" class="ai-write__progress">
      <AiTaskProgress :status="phase === 'submitting' ? 'submitting' : 'streaming'" />
      <pre v-if="phase === 'streaming'" class="ai-write__stream">{{ streamText }}<span class="ai-write__cursor" aria-hidden="true">▍</span></pre>
    </div>

    <div v-if="phase === 'error'" class="ai-write__error" role="alert">
      <AiTaskProgress status="error" :error="errorMsg" />
      <div class="ai-write__error-actions">
        <button type="button" class="ai-write__retry" @click="handleRetry">重试</button>
        <button type="button" class="ai-write__reset" @click="reset">重新输入</button>
      </div>
    </div>

    <section v-if="phase === 'done'" class="ai-write__result">
      <AiTaskProgress status="done" />
      <h2 class="ai-write__result-title">{{ resultTitle }}</h2>
      <div class="ai-write__preview markdown-body" v-html="renderedResult" />
      <div class="ai-write__result-actions">
        <button type="button" class="ai-write__save" :disabled="saving" @click="saveAsArticle">
          {{ saving ? '保存中…' : '保存为新文章' }}
        </button>
        <button type="button" class="ai-write__reset" @click="reset">重新写作</button>
      </div>
      <p v-if="saveMessage" class="ai-write__message" role="status">{{ saveMessage }}</p>
    </section>
  </main>
</template>

<style scoped>
.ai-write {
  max-width: 860px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.ai-write__header {
  margin-bottom: 20px;
}

.ai-write__title {
  margin: 0;
  font-size: 24px;
}

.ai-write__intro {
  margin: 8px 0 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.ai-write__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-write__tabs {
  display: flex;
  gap: 6px;
}

.ai-write__tab {
  padding: 7px 18px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.ai-write__tab--active {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.ai-write__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-write__field-label {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.ai-write__textarea,
.ai-write__input {
  box-sizing: border-box;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-secondary);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  outline: none;
}

.ai-write__textarea {
  resize: vertical;
}

.ai-write__textarea:focus,
.ai-write__input:focus {
  border-color: var(--xl-color-primary);
}

.ai-write__submit {
  align-self: flex-start;
  padding: 9px 24px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.ai-write__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-write__submit:hover:not(:disabled) {
  background: var(--xl-color-primary-hover);
}

.ai-write__progress {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-write__stream {
  margin: 0;
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

.ai-write__cursor {
  color: var(--xl-color-ai);
}

.ai-write__error {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-write__error-actions,
.ai-write__result-actions {
  display: flex;
  gap: 10px;
}

.ai-write__retry,
.ai-write__save {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.ai-write__save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-write__save:hover:not(:disabled),
.ai-write__retry:hover {
  background: var(--xl-color-primary-hover);
}

.ai-write__reset {
  padding: 8px 18px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.ai-write__reset:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.ai-write__result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-write__result-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.4;
}

.ai-write__preview {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.ai-write__message {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

/* Markdown 预览样式（B11）：与设计 token 对齐 */
.markdown-body {
  color: var(--xl-text-primary);
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: break-word;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.4em 0 0.6em;
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
  background: var(--xl-text-primary);
  color: #fff;
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
</style>
