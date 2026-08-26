<script setup lang="ts">
// 全站悬浮 AI 助理：右下角悬浮球（AI 主色），点击展开面板。
// 不分登录态均可使用：登录用户走登录态流（会话/历史能力），访客走公开单次问答；
// 会话仅存于组件内存（不写历史、不落盘），刷新页面后即清空。消息流含追问 chips 与存草稿入口。
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { useSessionStore } from '@/stores/session'
import { streamChat } from '@/modules/chat/api/chat'
import { streamPublicChat } from '@/modules/chat/api/publicChat'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import CitationCard from '@/modules/chat/components/CitationCard.vue'
import FollowupChips from '@/modules/chat/components/FollowupChips.vue'
import { buildDraftContent, buildDraftTitle, saveChatDraft } from '@/modules/chat/utils/draft'

import type { Citation } from '@/modules/chat/api/chat'

interface PanelMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
  /** 本次回答下方的追问建议（后端 followups 事件）。 */
  followups: string[]
  /** 本条回答对应的用户问题（存为草稿时作标题）。 */
  question: string
  streaming: boolean
}

const session = useSessionStore()

const open = ref(false)
const messages = ref<PanelMessage[]>([])
const askInput = ref('')
const asking = ref(false)
const listEl = ref<HTMLElement | null>(null)
const savingDraftId = ref<string | null>(null)

function scrollToBottom(): void {
  void nextTick(() => {
    listEl.value?.scrollTo({ top: listEl.value.scrollHeight })
  })
}

/** 发起一轮提问：来自追问 chips 时携带 question，否则取输入框文本。 */
async function send(question = ''): Promise<void> {
  const query = (question.trim() || askInput.value.trim()).trim()
  if (!query || asking.value) return
  if (!question.trim()) askInput.value = ''
  messages.value.push({
    id: `float-${Date.now()}`,
    role: 'user',
    content: query,
    citations: [],
    followups: [],
    question: '',
    streaming: false,
  })
  // 须用 reactive 代理后再入列，onChunk 持有的引用才能触发流式重渲染
  const assistant = reactive<PanelMessage>({
    id: `float-${Date.now()}-assistant`,
    role: 'assistant',
    content: '',
    citations: [],
    followups: [],
    question: query,
    streaming: true,
  })
  messages.value.push(assistant)
  asking.value = true
  scrollToBottom()

  const onChunk = (text: string): void => {
    assistant.content += text
    scrollToBottom()
  }
  const onCitations = (citations: Citation[]): void => {
    assistant.citations = citations
  }
  const onFollowups = (followups: string[]): void => {
    assistant.followups = followups
  }
  const onDone = (): void => undefined

  const controller = new AbortController()
  try {
    if (session.loggedIn) {
      await streamChat(
        { query },
        { onChunk, onTool: () => undefined, onCitations, onFollowups, onDone },
        controller.signal,
      )
    } else {
      await streamPublicChat(
        query,
        { onChunk, onCitations, onFollowups, onDone },
        controller.signal,
      )
    }
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

function handleFollowup(question: string): void {
  void send(question)
}

/** 将回答正文（含引用来源清单）存为新知识草稿。 */
async function saveAsDraft(message: PanelMessage): Promise<void> {
  if (!session.loggedIn || savingDraftId.value) return
  savingDraftId.value = message.id
  try {
    await saveChatDraft(
      buildDraftTitle(message.question),
      buildDraftContent(message.content, message.citations),
    )
    ElMessage.success('已存入草稿，可到创作中心完善')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '存入草稿失败')
  } finally {
    savingDraftId.value = null
  }
}
</script>

<template>
  <div class="floating-assistant">
    <Transition name="floating-assistant__pop">
      <section
        v-if="open"
        class="floating-assistant__panel"
        role="dialog"
        aria-label="「小光」AI 助理对话面板"
      >
        <header class="floating-assistant__header">
          <span class="floating-assistant__logo" aria-hidden="true">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M7.9 20A9 9 0 1 0 4 16.1L2 22l5.9-2Z" />
            </svg>
          </span>
          <div class="floating-assistant__heading">
            <h2 class="floating-assistant__title">小光 · AI 助理</h2>
            <span class="floating-assistant__subtitle">
              {{ session.loggedIn ? '登录态会话' : '访客模式，仅当前会话' }}
            </span>
          </div>
          <button
            type="button"
            class="floating-assistant__close"
            aria-label="关闭"
            @click="open = false"
          >
            ×
          </button>
        </header>

        <div ref="listEl" class="floating-assistant__messages">
          <p v-if="messages.length === 0" class="floating-assistant__empty">
            你好，我是「小光」，基于本站知识回答你的问题。
          </p>
          <div
            v-for="message in messages"
            :key="message.id"
            class="panel-message"
            :class="`panel-message--${message.role}`"
          >
            <div class="panel-message__bubble">
              <!-- 小光回答走 Markdown 渲染（DOMPurify 清洗）；用户消息保持纯文本防 XSS -->
              <div
                v-if="message.role === 'assistant'"
                class="panel-message__text panel-message__text--md markdown-body"
                v-html="renderMarkdown(message.content)"
              ></div>
              <p v-else class="panel-message__text">{{ message.content }}</p>
              <span v-if="message.streaming" class="panel-message__cursor" aria-hidden="true"
                >▍</span
              >
              <div v-if="message.citations.length > 0" class="panel-message__citations">
                <CitationCard
                  v-for="(citation, index) in message.citations"
                  :key="`${citation.knowledgeId}-${citation.chunkSeq}-${index}`"
                  :citation="citation"
                  :index="index + 1"
                />
              </div>
              <FollowupChips
                v-if="message.role === 'assistant' && message.followups.length > 0"
                :questions="message.followups"
                :disabled="asking"
                @select="handleFollowup"
              />
              <div
                v-if="
                  session.loggedIn &&
                  message.role === 'assistant' &&
                  message.content &&
                  !message.streaming
                "
                class="panel-message__draft"
              >
                <button
                  type="button"
                  class="panel-message__draft-btn"
                  :disabled="savingDraftId === message.id"
                  @click="saveAsDraft(message)"
                >
                  {{ savingDraftId === message.id ? '存入中…' : '存为知识草稿' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <form class="floating-assistant__composer" @submit.prevent="send()">
          <input
            v-model="askInput"
            class="floating-assistant__input"
            type="text"
            placeholder="输入你的问题…"
          />
          <el-button
            type="primary"
            class="floating-assistant__send"
            native-type="submit"
            :disabled="asking || !askInput.trim()"
          >
            {{ asking ? '回复中' : '发送' }}
          </el-button>
        </form>
      </section>
    </Transition>

    <button
      type="button"
      class="floating-assistant__ball"
      :aria-label="open ? '收起「小光」' : '打开「小光」'"
      @click="open = !open"
    >
      <svg
        class="floating-assistant__ball-icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M7.9 20A9 9 0 1 0 4 16.1L2 22l5.9-2Z" />
        <path
          d="M18.5 3l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9.9-2.1Z"
          fill="currentColor"
          stroke="none"
        />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.floating-assistant__ball {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 60;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
  color: #fff;
  box-shadow: var(--xl-shadow-lg);
  cursor: pointer;
  transition:
    transform var(--xl-transition),
    box-shadow var(--xl-transition);
}

.floating-assistant__ball:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--xl-color-ai) 45%, transparent);
}

.floating-assistant__ball-icon {
  width: 26px;
  height: 26px;
}

.floating-assistant__panel {
  position: fixed;
  right: 24px;
  bottom: 88px;
  z-index: 60;
  display: flex;
  flex-direction: column;
  width: 360px;
  max-width: calc(100vw - 24px);
  height: 520px;
  max-height: calc(100vh - 120px);
  overflow: hidden;
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-lg);
}

.floating-assistant__pop-enter-active,
.floating-assistant__pop-leave-active {
  transition:
    opacity var(--xl-transition),
    transform var(--xl-transition);
}

.floating-assistant__pop-enter-from,
.floating-assistant__pop-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

.floating-assistant__header {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-3) var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
  background: color-mix(in srgb, var(--xl-color-ai) 8%, var(--xl-bg-surface));
}

.floating-assistant__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
  color: #fff;
}

.floating-assistant__logo svg {
  width: 16px;
  height: 16px;
}

.floating-assistant__heading {
  flex: 1;
  min-width: 0;
}

.floating-assistant__title {
  margin: 0;
  font-size: 15px;
}

.floating-assistant__subtitle {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.floating-assistant__close {
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.floating-assistant__messages {
  flex: 1;
  padding: var(--xl-space-3);
  overflow-y: auto;
}

.floating-assistant__empty {
  margin: 0;
  padding: var(--xl-space-6) 0;
  text-align: center;
  color: var(--xl-text-muted);
  font-size: 13px;
}

.panel-message {
  display: flex;
  margin-bottom: var(--xl-space-3);
}

.panel-message--user {
  justify-content: flex-end;
}

.panel-message--assistant {
  justify-content: flex-start;
}

.panel-message__bubble {
  max-width: 88%;
  padding: 8px 12px;
  border-radius: 12px;
  background: var(--xl-bg-secondary);
}

.panel-message--user .panel-message__bubble {
  background: color-mix(in srgb, var(--xl-color-primary) 12%, transparent);
}

.panel-message__text {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

/* Markdown 消息体：块级标签自带分段，取消 pre-wrap 避免标签间换行被重复渲染 */
.panel-message__text--md {
  white-space: normal;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.5em;
}

.markdown-body :deep(p) {
  margin: 0.4em 0;
}

.markdown-body :deep(p:first-child),
.markdown-body :deep(p:last-child) {
  margin-block: 0;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 0.9em 0 0.4em;
}

.markdown-body :deep(a) {
  color: var(--xl-color-primary);
}

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--xl-border) 70%, transparent);
  font-family: var(--xl-font-mono);
  font-size: 12px;
}

.markdown-body :deep(pre) {
  margin: 0.5em 0;
  padding: var(--xl-space-2) var(--xl-space-3);
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
  margin: 0.5em 0;
  padding: 0 var(--xl-space-3);
  border-left: 3px solid var(--xl-color-primary);
  color: var(--xl-text-secondary);
}

.panel-message__cursor {
  color: var(--xl-color-ai);
}

.panel-message__citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.panel-message__draft {
  margin-top: 8px;
}

.panel-message__draft-btn {
  padding: 3px 10px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.panel-message__draft-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.panel-message__draft-btn:hover:not(:disabled) {
  border-color: var(--xl-color-ai);
  color: var(--xl-color-ai);
}

.floating-assistant__composer {
  display: flex;
  gap: 8px;
  padding: var(--xl-space-3);
  border-top: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.floating-assistant__input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: var(--xl-bg-page);
  color: var(--xl-text-primary);
  font-size: 13px;
  outline: none;
}

.floating-assistant__input:focus {
  border-color: var(--xl-color-primary);
}

.floating-assistant__send {
  background: var(--xl-color-ai);
  border-color: var(--xl-color-ai);
}

.floating-assistant__send:hover {
  background: var(--xl-color-success);
  border-color: var(--xl-color-success);
}
</style>
