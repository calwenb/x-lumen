<script setup lang="ts">
// AI 写作页（B11）：主题/草稿/完整知识三种输入，流式打字展示生成过程，
// 完成后展示标题 + Markdown 预览，可保存为新知识（走 content createKnowledge，归属库必选，决策 D16）。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createKnowledge } from '@/modules/content/api/knowledge'
import { fetchWritingTask, retryWritingTask, submitWriting } from '@/modules/ai/api/writing'
import { fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import type { KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import { streamSse } from '@/modules/ai/utils/sse'
import { SseEventName } from '@/modules/ai/utils/sseEvent'
import AiTaskProgress from '@/modules/ai/components/AiTaskProgress.vue'

import type { WritingRequest } from '@/modules/ai/api/writing'
import type { SseEvent } from '@/modules/ai/utils/sse'

type WriteMode = 'topic' | 'draft' | 'content'
type WritePhase = 'idle' | 'submitting' | 'streaming' | 'done' | 'error'

const MODES: ReadonlyArray<{ value: WriteMode; label: string }> = [
  { value: 'topic', label: '按主题' },
  { value: 'draft', label: '按草稿' },
  { value: 'content', label: '完整知识' },
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
/** 多步工作流进度（SSE progress 事件，0~100）。 */
const taskProgress = ref(0)

/** 保存为新知识的目标库（决策 D16：单库单目录，创建后不可更换）。 */
const knowledgeBases = ref<KnowledgeBase[]>([])
const kbId = ref('')

let controller: AbortController | null = null

onMounted(async () => {
  try {
    knowledgeBases.value = await fetchKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  }
})

const canSubmit = computed(() => {
  if (mode.value === 'topic') return topic.value.trim().length > 0
  if (mode.value === 'draft') return draft.value.trim().length > 0
  return contentBody.value.trim().length > 0
})

const renderedResult = computed(() =>
  resultContent.value ? renderMarkdown(resultContent.value) : '',
)

/** 多步工作流阶段文案（单次生成模式进度同样走 10→90）。 */
const stepLabel = computed(() => {
  const p = taskProgress.value
  if (p < 20) return '正在准备提纲…'
  if (p < 85) return '正在分章生成…'
  if (p < 90) return '正在自审…'
  if (p < 100) return '正在修订…'
  return '已完成'
})

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
  taskProgress.value = 0
  controller = new AbortController()
  try {
    await streamSse(
      `/tasks/${id}/events`,
      { method: 'GET', signal: controller.signal },
      (event) => {
        handleEvent(id, event)
      },
    )
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
  if (event.event === SseEventName.progress) {
    try {
      const parsed = JSON.parse(event.data) as { progress?: number }
      if (typeof parsed.progress === 'number') taskProgress.value = parsed.progress
    } catch {
      // 进度解析失败忽略
    }
    return
  }
  if (event.event === SseEventName.chunk) {
    // chunk 事件 data 为 JSON { taskId, sequence, content }，解析 content 增量而非展示原始 JSON。
    try {
      const parsed = JSON.parse(event.data) as { content?: string }
      if (typeof parsed.content === 'string') streamText.value += parsed.content
      else streamText.value += event.data
    } catch {
      streamText.value += event.data
    }
    return
  }
  if (event.event === SseEventName.error) {
    errorMsg.value = event.data
    phase.value = 'error'
    controller?.abort()
    return
  }
  if (event.event === SseEventName.done) {
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
  taskProgress.value = 0
}

async function saveAsKnowledge(): Promise<void> {
  const title = resultTitle.value.trim()
  const body = resultContent.value.trim()
  if (!title || !body) {
    saveMessage.value = '生成内容不完整，请重新生成后再保存'
    return
  }
  if (!kbId.value) {
    saveMessage.value = '请选择知识库'
    return
  }
  saving.value = true
  saveMessage.value = ''
  try {
    const created = await createKnowledge({
      title,
      content: body,
      kbId: kbId.value,
      tags: [],
    })
    await router.push({ name: 'knowledge-edit', params: { id: created.id } })
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
      <p class="ai-write__intro">
        「小光」根据主题、草稿或完整知识，流式生成一篇可直接发布的 Markdown 知识。
      </p>
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
        <textarea
          v-model="topic"
          class="ai-write__textarea"
          rows="4"
          placeholder="例如：Spring Boot 自动配置原理与实践"
        />
      </label>
      <label v-else-if="mode === 'draft'" class="ai-write__field">
        <span class="ai-write__field-label">草稿 / 提纲</span>
        <textarea
          v-model="draft"
          class="ai-write__textarea"
          rows="8"
          placeholder="粘贴你的草稿或提纲，小光会帮你扩写润色…"
        />
      </label>
      <template v-else>
        <label class="ai-write__field">
          <span class="ai-write__field-label">知识标题（可选）</span>
          <input
            v-model="contentTitle"
            class="ai-write__input"
            type="text"
            placeholder="例如：深入理解 JVM 内存模型"
          />
        </label>
        <label class="ai-write__field">
          <span class="ai-write__field-label">完整知识</span>
          <textarea
            v-model="contentBody"
            class="ai-write__textarea"
            rows="12"
            placeholder="粘贴完整知识，小光会帮你润色改写、优化结构…"
          />
        </label>
      </template>

      <el-button type="primary" class="ai-write__submit" :disabled="!canSubmit" @click="submit">
        开始写作
      </el-button>
    </section>

    <div v-if="phase === 'submitting' || phase === 'streaming'" class="ai-write__progress">
      <AiTaskProgress :status="phase === 'submitting' ? 'submitting' : 'streaming'" />
      <div v-if="phase === 'streaming'" class="ai-write__steps">
        <el-progress :percentage="taskProgress" :stroke-width="6" :format="() => stepLabel" />
      </div>
      <pre
        v-if="phase === 'streaming'"
        class="ai-write__stream">{{ streamText }}<span class="ai-write__cursor" aria-hidden="true">▍</span></pre>
    </div>

    <div v-if="phase === 'error'" class="ai-write__error" role="alert">
      <AiTaskProgress status="error" :error="errorMsg" />
      <div class="ai-write__error-actions">
        <el-button type="primary" plain @click="handleRetry">重试</el-button>
        <el-button @click="reset">重新输入</el-button>
      </div>
    </div>

    <section v-if="phase === 'done'" class="ai-write__done">
      <aside class="ai-write__task-rail">
        <h2 class="ai-write__rail-title">AI 写作</h2>
        <p class="ai-write__rail-intro">小光根据主题或草稿生成完整知识</p>

        <div class="ai-write__input-summary">
          <span class="ai-write__input-label">本次输入</span>
          <p class="ai-write__input-text">
            {{ mode === 'topic' ? topic : mode === 'draft' ? draft : contentTitle || contentBody }}
          </p>
        </div>

        <ul class="ai-write__steps">
          <li class="ai-write__step ai-write__step--done">
            <span class="ai-write__step-check" aria-hidden="true">✓</span>
            <span class="ai-write__step-name">大纲</span>
          </li>
          <li class="ai-write__step ai-write__step--done">
            <span class="ai-write__step-check" aria-hidden="true">✓</span>
            <span class="ai-write__step-name">分章</span>
          </li>
          <li class="ai-write__step ai-write__step--done">
            <span class="ai-write__step-check" aria-hidden="true">✓</span>
            <span class="ai-write__step-name">自审</span>
          </li>
          <li class="ai-write__step ai-write__step--done">
            <span class="ai-write__step-check" aria-hidden="true">✓</span>
            <span class="ai-write__step-name">修订</span>
          </li>
        </ul>

        <el-button class="ai-write__rewrite" @click="reset">重新写作</el-button>
      </aside>

      <div class="ai-write__canvas">
        <AiTaskProgress status="done" />
        <h2 class="ai-write__result-title">{{ resultTitle }}</h2>
        <div class="ai-write__preview markdown-body" v-html="renderedResult" />
        <p v-if="saveMessage" class="ai-write__message" role="status">{{ saveMessage }}</p>
      </div>

      <div class="ai-write__saveband">
        <label class="ai-write__saveband-label" for="ai-write-kb">所属知识库</label>
        <el-select
          id="ai-write-kb"
          v-model="kbId"
          class="ai-write__kb"
          placeholder="所属知识库（必选）"
          aria-label="所属知识库"
        >
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button type="primary" class="ai-write__save" :loading="saving" @click="saveAsKnowledge">
          {{ saving ? '保存中' : '保存为新知识' }}
        </el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.ai-write {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px var(--xl-content-pad) 64px;
}

.ai-write__header {
  margin-bottom: 20px;
}

.ai-write__title {
  margin: 0;
  font-size: 26px;
}

.ai-write__intro {
  margin: 8px 0 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
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
  font-size: 14px;
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
  font-size: 14px;
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
  font-size: var(--xl-fs-body);
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
  box-shadow: var(--xl-shadow-sm);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: var(--xl-fs-body);
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

.ai-write__result-title {
  margin: 0;
  font-size: var(--xl-fs-h2);
  font-weight: var(--xl-fs-h2-w);
  line-height: var(--xl-fs-h2-lh);
  letter-spacing: var(--xl-fs-h2-track);
}

.ai-write__preview {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow-y: auto;
  max-height: 62vh;
  flex: 1;
}

.ai-write__message {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

/* 完成态（B11-S）：36/64 任务工作台 */
.ai-write__done {
  display: grid;
  grid-template-columns: 36fr 64fr;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: var(--xl-space-6);
  align-items: stretch;
}

.ai-write__task-rail {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
  padding: var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.ai-write__rail-title {
  margin: 0;
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
  color: var(--xl-text-primary);
}

.ai-write__rail-intro {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.ai-write__input-summary {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-1);
  padding: var(--xl-space-3);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-secondary);
}

.ai-write__input-label {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  font-weight: 600;
}

.ai-write__input-text {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ai-write__steps {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-write__step {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-2) var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.ai-write__step-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--xl-color-ai) 12%, transparent);
  color: var(--xl-color-ai);
  font-size: var(--xl-fs-caption);
  line-height: 1;
}

.ai-write__step--done {
  border-color: color-mix(in srgb, var(--xl-color-ai) 28%, var(--xl-border));
}

.ai-write__step--done .ai-write__step-name {
  color: var(--xl-text-primary);
}

.ai-write__rewrite {
  margin-top: auto;
}

.ai-write__canvas {
  grid-column: 2;
  grid-row: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-4);
}

.ai-write__saveband {
  grid-column: 1 / -1;
  grid-row: 2;
  display: flex;
  align-items: center;
  gap: var(--xl-space-3);
  padding: var(--xl-space-4) var(--xl-space-6);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.ai-write__saveband-label {
  color: var(--xl-text-secondary);
  font-size: 14px;
  font-weight: 600;
}

.ai-write__kb {
  width: 280px;
}

.ai-write__save {
  margin-left: auto;
}

@media (width <= 900px) {
  .ai-write__done {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
  }

  .ai-write__task-rail,
  .ai-write__canvas,
  .ai-write__saveband {
    grid-column: 1;
    grid-row: auto;
  }

  .ai-write__kb {
    width: 100%;
  }
}

/* Markdown 预览样式（B11）：与设计 token 对齐 */
.markdown-body {
  color: var(--xl-text-primary);
  font-size: 16px;
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
  font-size: 14px;
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
