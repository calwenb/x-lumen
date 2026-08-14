<script setup lang="ts">
// AI 助理（B00/D01 合一，F-0701）：左侧会话列表（登录可见）+ 右侧消息流（流式打字 + 引用溯源）。
// 访客无会话功能，单次问答；登录用户可选会话/新对话，回答附带 [序号] 引用卡片。
import { nextTick, onMounted, ref } from 'vue'
import { Plus, UserFilled } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'
import {
  createConversation,
  fetchConversations,
  fetchMessages,
  streamChat,
} from '@/modules/chat/api/chat'
import CitationCard from '@/modules/chat/components/CitationCard.vue'

import type { ChatMessage, Citation, Conversation } from '@/modules/chat/api/chat'

interface ChatItem {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
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

function toChatItem(message: ChatMessage): ChatItem {
  return {
    id: message.id,
    role: message.role,
    content: message.content,
    citations: message.citations,
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
    streaming: false,
  })
  const assistant: ChatItem = {
    id: `local-${Date.now()}-assistant`,
    role: 'assistant',
    content: '',
    citations: [],
    streaming: true,
  }
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()

  const controller = new AbortController()
  let newConversationId = ''
  try {
    await streamChat(
      { query, ...(currentId.value ? { conversationId: currentId.value } : {}) },
      {
        onChunk: (text) => {
          assistant.content += text
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
            <p class="chat-message__text">
              {{ message.content
              }}<span v-if="message.streaming" class="chat-message__cursor" aria-hidden="true"
                >▍</span
              >
            </p>
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

.chat-message__cursor {
  color: var(--xl-color-ai);
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
