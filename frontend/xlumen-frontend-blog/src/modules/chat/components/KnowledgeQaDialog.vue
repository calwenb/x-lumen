<script setup lang="ts">
// 知识级问答弹窗（D02）：单篇问答流式打字 + 引用溯源；详情页集成点。
// KB-3 检索范围（决策 D13）：默认锁定当前知识所属库（传 kbId），可切换「全部可见库」。
import { nextTick, reactive, ref } from 'vue'

import { streamKnowledgeAsk } from '@/modules/chat/api/chat'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import CitationCard from '@/modules/chat/components/CitationCard.vue'

import type { Citation } from '@/modules/chat/api/chat'

const props = defineProps<{
  knowledgeId: string
  knowledgeTitle: string
  /** 当前知识所属知识库 ID（为空则无法锁定本库，默认全部可见库）。 */
  kbId?: string | null
  /** 所属知识库名称（选择器展示用）。 */
  kbName?: string
}>()

const emit = defineEmits<{
  close: []
}>()

interface QaMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
  streaming: boolean
}

/** 检索范围：kb=锁定当前库（默认）；all=全部可见库。 */
const scope = ref<'kb' | 'all'>(props.kbId ? 'kb' : 'all')

const messages = ref<QaMessage[]>([])
const draft = ref('')
const asking = ref(false)
const listEl = ref<HTMLElement | null>(null)

function scrollToBottom(): void {
  void nextTick(() => {
    listEl.value?.scrollTo({ top: listEl.value.scrollHeight })
  })
}

async function send(): Promise<void> {
  const query = draft.value.trim()
  if (!query || asking.value) return
  draft.value = ''
  messages.value.push({
    id: `qa-${Date.now()}`,
    role: 'user',
    content: query,
    citations: [],
    streaming: false,
  })
  // BUG-002：须用 reactive 代理后再入列，onChunk 持有的引用才能触发流式重渲染
  const assistant = reactive<QaMessage>({
    id: `qa-${Date.now()}-assistant`,
    role: 'assistant',
    content: '',
    citations: [],
    streaming: true,
  })
  messages.value.push(assistant)
  asking.value = true
  scrollToBottom()

  const controller = new AbortController()
  try {
    await streamKnowledgeAsk(
      props.knowledgeId,
      query,
      {
        onChunk: (text) => {
          assistant.content += text
          scrollToBottom()
        },
        onCitations: (citations) => {
          assistant.citations = citations
        },
        onDone: () => undefined,
      },
      controller.signal,
      // KB-3：本库=传 kbId 限定单库；全部可见库=allVisible=true
      scope.value === 'kb' && props.kbId
        ? { kbId: props.kbId }
        : { allVisible: true },
    )
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError') && !assistant.content) {
      assistant.content = error instanceof Error ? error.message : '回答失败，请稍后重试'
    }
  } finally {
    assistant.streaming = false
    asking.value = false
    scrollToBottom()
  }
}
</script>

<template>
  <div class="qa-dialog__overlay" @click.self="emit('close')">
    <div
      class="qa-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="`就「${knowledgeTitle}」向小光提问`"
    >
      <header class="qa-dialog__header">
        <div class="qa-dialog__heading">
          <h2 class="qa-dialog__title">问「小光」</h2>
          <span class="qa-dialog__subtitle">{{ knowledgeTitle }}</span>
        </div>
        <button type="button" class="qa-dialog__close" aria-label="关闭" @click="emit('close')">
          ×
        </button>
      </header>

      <div class="qa-dialog__scope" aria-label="检索范围">
        <el-radio-group v-model="scope" size="small">
          <el-radio value="kb" :disabled="!kbId">
            {{ kbName ? `本库（${kbName}）` : '本库' }}
          </el-radio>
          <el-radio value="all">全部可见库</el-radio>
        </el-radio-group>
      </div>

      <div ref="listEl" class="qa-dialog__messages">
        <p v-if="messages.length === 0" class="qa-dialog__empty">
          就本知识内容向「小光」提问，回答可溯源到原文段落。
        </p>
        <div
          v-for="message in messages"
          :key="message.id"
          class="qa-message"
          :class="`qa-message--${message.role}`"
        >
          <div class="qa-message__bubble">
            <!-- 小光回答走 Markdown 渲染（DOMPurify 清洗，人设约定）；用户消息保持纯文本防 XSS -->
            <div
              v-if="message.role === 'assistant'"
              class="qa-message__text qa-message__text--md markdown-body"
              v-html="renderMarkdown(message.content)"
            ></div>
            <p v-else class="qa-message__text">{{ message.content }}</p>
            <span
              v-if="message.streaming"
              class="qa-message__cursor"
              aria-hidden="true"
              >▍</span
            >
            <div v-if="message.citations.length > 0" class="qa-message__citations">
              <CitationCard
                v-for="(citation, index) in message.citations"
                :key="`${citation.knowledgeId}-${citation.chunkSeq}-${index}`"
                :citation="citation"
                :index="index + 1"
              />
            </div>
          </div>
        </div>
      </div>

      <form class="qa-dialog__composer" @submit.prevent="send">
        <input v-model="draft" class="qa-dialog__input" type="text" placeholder="就本知识提问…" />
        <el-button
          type="primary"
          class="qa-dialog__send"
          native-type="submit"
          :disabled="asking || !draft.trim()"
        >
          {{ asking ? '回复中' : '提问' }}
        </el-button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.qa-dialog__overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--xl-space-4);
  background: color-mix(in srgb, var(--xl-text-primary) 40%, transparent);
}

.qa-dialog {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 560px;
  max-height: 80vh;
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-lg);
  overflow: hidden;
}

.qa-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
}

.qa-dialog__heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.qa-dialog__title {
  margin: 0;
  font-size: 16px;
}

.qa-dialog__subtitle {
  color: var(--xl-text-secondary);
  font-size: 13px;
  overflow-wrap: break-word;
}

.qa-dialog__close {
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.qa-dialog__scope {
  display: flex;
  padding: var(--xl-space-2) var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
}

.qa-dialog__messages {
  flex: 1;
  padding: var(--xl-space-4);
  overflow-y: auto;
}

.qa-dialog__empty {
  margin: 0;
  padding: var(--xl-space-6) 0;
  text-align: center;
  color: var(--xl-text-muted);
  font-size: 13px;
}

.qa-message {
  display: flex;
  margin-bottom: var(--xl-space-3);
}

.qa-message--user {
  justify-content: flex-end;
}

.qa-message--assistant {
  justify-content: flex-start;
}

.qa-message__bubble {
  max-width: 85%;
  padding: 8px 12px;
  border-radius: 12px;
  background: var(--xl-bg-secondary);
}

.qa-message--user .qa-message__bubble {
  background: color-mix(in srgb, var(--xl-color-primary) 12%, transparent);
}

.qa-message__text {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

/* Markdown 消息体：块级标签自带分段，取消 pre-wrap 避免标签间换行被重复渲染 */
.qa-message__text--md {
  white-space: normal;
}

.markdown-body :deep(p) {
  margin: 0.6em 0;
}

.markdown-body :deep(p:first-child),
.markdown-body :deep(p:last-child) {
  margin-block: 0;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.1em 0 0.5em;
}

.markdown-body :deep(a) {
  color: var(--xl-color-primary);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.6em;
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--xl-border) 70%, transparent);
  font-family: var(--xl-font-mono);
  font-size: 13px;
}

.markdown-body :deep(pre) {
  margin: 0.6em 0;
  padding: var(--xl-space-3);
  overflow-x: auto;
  border-radius: 8px;
  background: var(--xl-text-primary);
  color: #fff;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  margin: 0.6em 0;
  padding: 0 var(--xl-space-3);
  border-left: 3px solid var(--xl-color-primary);
  color: var(--xl-text-secondary);
}

.qa-message__cursor {
  color: var(--xl-color-ai);
}

.qa-message__citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.qa-dialog__composer {
  display: flex;
  gap: 8px;
  padding: var(--xl-space-3) var(--xl-space-4);
  border-top: 1px solid var(--xl-border);
}

.qa-dialog__input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: var(--xl-bg-page);
  color: var(--xl-text-primary);
  font-size: 14px;
  outline: none;
}

.qa-dialog__input:focus {
  border-color: var(--xl-color-primary);
}

.qa-dialog__send {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-ai);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.qa-dialog__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.qa-dialog__send:hover:not(:disabled) {
  opacity: 0.9;
}
</style>
