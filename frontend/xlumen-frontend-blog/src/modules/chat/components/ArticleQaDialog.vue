<script setup lang="ts">
// 文章级问答弹窗（D02）：单篇问答流式打字 + 引用溯源；详情页集成点（详情页不在本次改动范围）。
import { nextTick, ref } from 'vue'

import { streamArticleAsk } from '@/modules/chat/api/chat'
import CitationCard from '@/modules/chat/components/CitationCard.vue'

import type { Citation } from '@/modules/chat/api/chat'

const props = defineProps<{
  articleId: string
  articleTitle: string
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
  const assistant: QaMessage = {
    id: `qa-${Date.now()}-assistant`,
    role: 'assistant',
    content: '',
    citations: [],
    streaming: true,
  }
  messages.value.push(assistant)
  asking.value = true
  scrollToBottom()

  const controller = new AbortController()
  try {
    await streamArticleAsk(
      props.articleId,
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
      :aria-label="`就「${articleTitle}」向小光提问`"
    >
      <header class="qa-dialog__header">
        <div class="qa-dialog__heading">
          <h2 class="qa-dialog__title">问「小光」</h2>
          <span class="qa-dialog__subtitle">{{ articleTitle }}</span>
        </div>
        <button type="button" class="qa-dialog__close" aria-label="关闭" @click="emit('close')">
          ×
        </button>
      </header>

      <div ref="listEl" class="qa-dialog__messages">
        <p v-if="messages.length === 0" class="qa-dialog__empty">
          就本文内容向「小光」提问，回答可溯源到原文段落。
        </p>
        <div
          v-for="message in messages"
          :key="message.id"
          class="qa-message"
          :class="`qa-message--${message.role}`"
        >
          <div class="qa-message__bubble">
            <p class="qa-message__text">
              {{ message.content
              }}<span v-if="message.streaming" class="qa-message__cursor" aria-hidden="true"
                >▍</span
              >
            </p>
            <div v-if="message.citations.length > 0" class="qa-message__citations">
              <CitationCard
                v-for="(citation, index) in message.citations"
                :key="`${citation.articleId}-${citation.chunkSeq}-${index}`"
                :citation="citation"
                :index="index + 1"
              />
            </div>
          </div>
        </div>
      </div>

      <form class="qa-dialog__composer" @submit.prevent="send">
        <input v-model="draft" class="qa-dialog__input" type="text" placeholder="就本文提问…" />
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
