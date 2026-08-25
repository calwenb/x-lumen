<script setup lang="ts">
// AI 助理（B00/D01 合一，F-0701）：左侧会话列表（登录可见）+ 右侧消息流（流式打字 + 引用溯源）。
// 访客无会话功能，单次问答；登录用户可选会话/新对话，回答附带 [序号] 引用卡片。
// KB-3 检索范围选择器（决策 D13/D16）：全部可见库（默认）/ 指定知识库；访客隐藏选择器默认全部。
import { nextTick, onMounted, reactive, ref } from 'vue'
import { Plus, UserFilled } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import {
  createConversation,
  fetchConversations,
  fetchMessages,
  streamChat,
} from '@/modules/chat/api/chat'
import { fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import CitationCard from '@/modules/chat/components/CitationCard.vue'
import { activeTools, doneTools } from '@/modules/chat/utils/toolPanel'

import type { ChatMessage, Citation, Conversation, ToolCallRecord, ToolEvent } from '@/modules/chat/api/chat'
import type { KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'

interface ChatItem {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
  /** 本次流式过程中的工具过程事件（IDEA-025：start/done 时序渲染）。 */
  tools: ToolEvent[]
  /** 历史回放的工具调用记录（来源 toolCallsJson）。 */
  toolCalls: ToolCallRecord[]
  streaming: boolean
}

const session = useSessionStore()

const conversations = ref<Conversation[]>([])
const conversationsLoading = ref(false)
const currentId = ref<string | null>(null)
const messages = ref<ChatItem[]>([])
const draft = ref('')
const sending = ref(false)
const creating = ref(false)
const listEl = ref<HTMLElement | null>(null)

// 检索范围（KB-3）：全部可见库（默认）/ 指定知识库；访客隐藏选择器。
const scopeMode = ref<'all' | 'kb'>('all')
const scopeKbId = ref('')
const knowledgeBases = ref<KnowledgeBase[]>([])
const basesLoading = ref(false)

function toChatItem(message: ChatMessage): ChatItem {
  return {
    id: message.id,
    role: message.role,
    content: message.content,
    citations: message.citations,
    tools: [],
    toolCalls: message.toolCalls,
    streaming: false,
  }
}

async function loadConversations(): Promise<void> {
  if (!session.loggedIn) return
  conversationsLoading.value = true
  try {
    conversations.value = await fetchConversations()
  } catch {
    // 会话列表加载失败不阻断问答
  } finally {
    conversationsLoading.value = false
  }
}

/** 我的知识库（登录用户可见；加载失败不阻断问答，选择器降级为仅「全部可见库」）。 */
async function loadKnowledgeBases(): Promise<void> {
  if (!session.loggedIn) return
  basesLoading.value = true
  try {
    knowledgeBases.value = await fetchKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  } finally {
    basesLoading.value = false
  }
}

async function selectConversation(id: string): Promise<void> {
  currentId.value = id
  messages.value = []
  try {
    const history = await fetchMessages(id)
    messages.value = history.map(toChatItem)
  } catch {
    messages.value = []
  }
  scrollToBottom()
}

async function startNewConversation(): Promise<void> {
  if (!session.loggedIn || creating.value) return
  creating.value = true
  try {
    const created = await createConversation('新对话')
    currentId.value = created.id
    messages.value = []
    await loadConversations()
  } catch {
    currentId.value = null
    messages.value = []
  } finally {
    creating.value = false
  }
  scrollToBottom()
}

function scrollToBottom(): void {
  void nextTick(() => {
    listEl.value?.scrollTo({ top: listEl.value.scrollHeight })
  })
}

async function send(): Promise<void> {
  const query = draft.value.trim()
  if (!query || sending.value) return
  draft.value = ''
  messages.value.push({
    id: `local-${Date.now()}`,
    role: 'user',
    content: query,
    citations: [],
    tools: [],
    toolCalls: [],
    streaming: false,
  })
  // BUG-002：须用 reactive 代理后再入列，onChunk 持有的引用才能触发流式重渲染
  const assistant = reactive<ChatItem>({
    id: `local-${Date.now()}-assistant`,
    role: 'assistant',
    content: '',
    citations: [],
    tools: [],
    toolCalls: [],
    streaming: true,
  })
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()

  const controller = new AbortController()
  let newConversationId = ''
  try {
    await streamChat(
      {
        query,
        ...(currentId.value ? { conversationId: currentId.value } : {}),
        // KB-3 检索范围：指定知识库时限定单库；默认不传=全部可见库
        ...(scopeMode.value === 'kb' && scopeKbId.value ? { kbId: scopeKbId.value } : {}),
      },
      {
        onChunk: (text) => {
          assistant.content += text
          scrollToBottom()
        },
        onTool: (event) => {
          assistant.tools.push(event)
          scrollToBottom()
        },
        onCitations: (citations) => {
          assistant.citations = citations
        },
        onDone: (result) => {
          newConversationId = result.conversationId
        },
      },
      controller.signal,
    )
    if (newConversationId && !currentId.value) {
      currentId.value = newConversationId
      await loadConversations()
    }
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError') && !assistant.content) {
      assistant.content = error instanceof Error ? error.message : '回答失败，请稍后重试'
    }
  } finally {
    assistant.streaming = false
    sending.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  void loadConversations()
  void loadKnowledgeBases()
})
</script>

<template>
  <main class="chat">
    <aside class="chat__sidebar">
      <header class="chat__sidebar-header">
        <h1 class="chat__title">小光 · AI 助理</h1>
      </header>

      <template v-if="session.loggedIn">
        <el-button
          type="primary"
          class="chat__new"
          :icon="Plus"
          :disabled="creating"
          @click="startNewConversation"
        >
          {{ creating ? '创建中…' : '新对话' }}
        </el-button>
        <div class="chat__scope" aria-label="检索范围">
          <el-radio-group v-model="scopeMode" size="small">
            <el-radio-button value="all">全部可见库</el-radio-button>
            <el-radio-button value="kb">指定知识库</el-radio-button>
          </el-radio-group>
          <el-select
            v-if="scopeMode === 'kb'"
            v-model="scopeKbId"
            class="chat__scope-select"
            placeholder="选择知识库"
            :loading="basesLoading"
            clearable
          >
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id" :label="kb.name" />
          </el-select>
        </div>
        <nav class="chat__conversations" aria-label="会话列表">
          <div v-if="conversationsLoading" class="chat__hint">会话加载中…</div>
          <div v-else-if="conversations.length === 0" class="chat__hint">暂无历史会话</div>
          <button
            v-for="conversation in conversations"
            :key="conversation.id"
            type="button"
            class="chat__conversation"
            :class="{ 'chat__conversation--active': conversation.id === currentId }"
            @click="selectConversation(conversation.id)"
          >
            {{ conversation.title || '未命名对话' }}
          </button>
        </nav>
      </template>
      <p v-else class="chat__guest-hint">访客模式：单次问答，不保留会话历史。</p>
    </aside>

    <section class="chat__main">
      <div ref="listEl" class="chat__messages">
        <div v-if="messages.length === 0" class="chat__empty">
          <div class="chat__empty-avatar" aria-hidden="true">小光</div>
          <p class="chat__empty-title">你好，我是「小光」</p>
          <p class="chat__empty-text">基于本站知识回答你的问题，回答会附带可溯源的引用。</p>
        </div>
        <div
          v-for="message in messages"
          :key="message.id"
          class="chat-message"
          :class="`chat-message--${message.role}`"
        >
          <div v-if="message.role === 'assistant'" class="chat-message__avatar chat-message__avatar--ai" aria-hidden="true">
            小光
          </div>
          <div class="chat-message__bubble">
            <p v-if="message.role === 'assistant'" class="chat-message__name">小光</p>
            <!-- 小光回答走 Markdown 渲染（DOMPurify 清洗，人设约定）；用户消息保持纯文本防 XSS -->
            <div
              v-if="message.role === 'assistant'"
              class="chat-message__text chat-message__text--md markdown-body"
              v-html="renderMarkdown(message.content)"
            ></div>
            <p v-else class="chat-message__text">{{ message.content }}</p>
            <span
              v-if="message.streaming"
              class="chat-message__cursor"
              aria-hidden="true"
              >▍</span
            >
            <!-- IDEA-025 工具过程：进行中状态行（正在检索） -->
            <div
              v-if="message.role === 'assistant' && activeTools(message.tools).length > 0"
              class="chat-message__active-tools"
            >
              <span
                v-for="tool in activeTools(message.tools)"
                :key="`active-${tool.seq}`"
                class="chat-message__tool-line"
              >
                {{ tool.name === 'knowledge.search' ? '正在检索知识库…' : `正在调用 ${tool.name}…` }}
              </span>
            </div>
            <!-- IDEA-025 工具轨迹：done 面板（流式过程）+ 历史回放 -->
            <details
              v-if="message.role === 'assistant' && doneTools(message.tools).length > 0"
              class="chat-message__tools"
            >
              <summary class="chat-message__tools-summary">
                调用了 {{ doneTools(message.tools).length }} 个工具
              </summary>
              <ul class="chat-message__tools-list">
                <li
                  v-for="tool in doneTools(message.tools)"
                  :key="`done-${tool.seq}`"
                  class="chat-message__tool"
                  :class="{ 'chat-message__tool--fail': tool.ok === false }"
                >
                  <span class="chat-message__tool-name">{{ tool.name }}</span>
                  <span v-if="tool.ok === false" class="chat-message__tool-status">失败</span>
                  <span v-else class="chat-message__tool-status">完成</span>
                  <span v-if="tool.summary" class="chat-message__tool-summary">{{ tool.summary }}</span>
                  <span v-if="tool.durationMs != null" class="chat-message__tool-duration">
                    {{ tool.durationMs }}ms
                  </span>
                </li>
              </ul>
            </details>
            <details
              v-else-if="message.role === 'assistant' && message.toolCalls.length > 0"
              class="chat-message__tools"
            >
              <summary class="chat-message__tools-summary">
                调用了 {{ message.toolCalls.length }} 个工具（历史）
              </summary>
              <ul class="chat-message__tools-list">
                <li v-for="call in message.toolCalls" :key="call.id" class="chat-message__tool">
                  <span class="chat-message__tool-name">{{ call.name || 'knowledge.search' }}</span>
                </li>
              </ul>
            </details>
            <div v-if="message.citations.length > 0" class="chat-message__citations">
              <CitationCard
                v-for="(citation, index) in message.citations"
                :key="`${citation.knowledgeId}-${citation.chunkSeq}-${index}`"
                :citation="citation"
                :index="index + 1"
              />
            </div>
          </div>
          <div v-if="message.role === 'user'" class="chat-message__avatar chat-message__avatar--user" aria-hidden="true">
            <el-icon><UserFilled /></el-icon>
          </div>
        </div>
      </div>

      <form class="chat__composer" @submit.prevent="send">
        <textarea
          v-model="draft"
          class="chat__input"
          rows="2"
          placeholder="输入你的问题…"
          @keydown.enter.exact.prevent="send"
        />
        <el-button
          type="primary"
          class="chat__send"
          native-type="submit"
          :disabled="sending || !draft.trim()"
        >
          {{ sending ? '回复中…' : '发送' }}
        </el-button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  height: calc(100vh - 56px);
}

.chat__sidebar {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-3);
  padding: var(--xl-space-4);
  border-right: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
  overflow-y: auto;
}

.chat__sidebar-header {
  padding-bottom: var(--xl-space-3);
  border-bottom: 1px solid var(--xl-border);
}

.chat__title {
  margin: 0;
  font-size: 16px;
}

.chat__new {
  width: 100%;
}

.chat__scope {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat__scope :deep(.el-radio-group) {
  display: flex;
}

.chat__scope :deep(.el-radio-button__inner) {
  font-size: 12px;
}

.chat__scope-select {
  width: 100%;
}

.chat__conversations {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat__hint,
.chat__guest-hint {
  padding: var(--xl-space-3) 0;
  color: var(--xl-text-muted);
  font-size: 13px;
}

.chat__conversation {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  overflow-wrap: break-word;
}

.chat__conversation:hover {
  background: var(--xl-bg-secondary);
}

.chat__conversation--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.chat__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat__messages {
  flex: 1;
  padding: var(--xl-space-4);
  overflow-y: auto;
}

.chat__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}

.chat__empty-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: var(--xl-space-4);
  border-radius: 50%;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  box-shadow: var(--xl-shadow-md);
}

.chat__empty-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.chat__empty-text {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-3);
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message--assistant {
  justify-content: flex-start;
}

.chat-message__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  margin-top: 2px;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.chat-message__avatar--ai {
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
}

.chat-message__avatar--user {
  background: color-mix(in srgb, var(--xl-color-primary) 18%, white);
  color: var(--xl-color-primary);
  font-size: 14px;
}

.chat-message__bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--xl-bg-secondary);
  box-shadow: var(--xl-shadow-sm);
}

.chat-message--user .chat-message__bubble {
  background: color-mix(in srgb, var(--xl-color-primary) 12%, transparent);
  box-shadow: none;
}

.chat-message__name {
  margin: 0 0 4px;
  color: var(--xl-color-ai);
  font-size: 12px;
  font-weight: 600;
}

.chat-message__text {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

/* Markdown 消息体：块级标签自带分段，取消 pre-wrap 避免标签间换行被重复渲染 */
.chat-message__text--md {
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

.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child),
.markdown-body :deep(h4:first-child) {
  margin-top: 0;
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

.markdown-body :deep(table) {
  margin: 0.6em 0;
  border-collapse: collapse;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 4px 10px;
  border: 1px solid var(--xl-border);
}

.chat-message__cursor {
  color: var(--xl-color-ai);
}

/* IDEA-025 工具过程展示：进行中状态行 + done/历史折叠面板 */
.chat-message__active-tools {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.chat-message__tool-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.chat-message__tool-line::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--xl-color-ai);
  animation: chat-pulse 1s ease-in-out infinite;
}

@keyframes chat-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

.chat-message__tools {
  margin-top: 8px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: var(--xl-bg-surface);
  padding: 6px 10px;
}

.chat-message__tools-summary {
  color: var(--xl-text-secondary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.chat-message__tools-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.chat-message__tool {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--xl-text-secondary);
  font-size: 12px;
}

.chat-message__tool-name {
  font-family: var(--xl-font-mono);
  font-size: 11px;
}

.chat-message__tool-status {
  padding: 1px 6px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--xl-color-success) 14%, transparent);
  color: var(--xl-color-success);
  font-size: 11px;
}

.chat-message__tool--fail .chat-message__tool-status {
  background: color-mix(in srgb, var(--xl-color-danger) 14%, transparent);
  color: var(--xl-color-danger);
}

.chat-message__tool-summary {
  color: var(--xl-text-muted);
  overflow-wrap: anywhere;
}

.chat-message__tool-duration {
  margin-left: auto;
  color: var(--xl-text-muted);
  font-size: 11px;
}

.chat-message__citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.chat__composer {
  display: flex;
  gap: 8px;
  padding: var(--xl-space-3) var(--xl-space-4);
  border-top: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.chat__input {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: var(--xl-bg-page);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  outline: none;
}

.chat__input:focus {
  border-color: var(--xl-color-primary);
}

.chat__send {
  align-self: flex-end;
  background: var(--xl-color-ai);
  border-color: var(--xl-color-ai);
}

.chat__send:hover {
  background: var(--xl-color-success);
  border-color: var(--xl-color-success);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--xl-color-ai) 40%, transparent);
}

@media (width <= 760px) {
  .chat {
    grid-template-columns: 1fr;
  }

  .chat__sidebar {
    border-right: none;
    border-bottom: 1px solid var(--xl-border);
    max-height: 200px;
  }
}
</style>
