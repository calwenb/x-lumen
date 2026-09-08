<script setup lang="ts">
// AI 助理（B00/D01 合一）：左侧会话列表（登录可见）+ 右侧消息流（流式打字 + 引用溯源）。
// 访客无会话功能，单次问答；登录用户可选会话/新对话，回答附带 [序号] 引用卡片。
// KB-3 检索范围选择器（决策 D13/D16）：全部可见库（默认）/ 指定知识库；访客隐藏选择器默认全部。
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  Brush,
  CircleCloseFilled,
  Clock,
  Collection,
  Delete,
  Plus,
  SuccessFilled,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { useSessionStore } from '@/stores/session'
import {
  clearConversationMessages,
  createConversation,
  deleteConversation,
  fetchConversations,
  fetchMessages,
  streamChat,
} from '@/modules/chat/api/chat'
import { fetchKnowledgeBases } from '@/modules/knowledge/api/knowledgeBase'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'
import { fetchKnowledges } from '@/modules/publishing/api/public'
import CitationCard from '@/modules/chat/components/CitationCard.vue'
import FollowupChips from '@/modules/chat/components/FollowupChips.vue'
import { activeTools, doneTools } from '@/modules/chat/utils/toolPanel'
import { buildDraftContent, buildDraftTitle, saveChatDraft } from '@/modules/chat/utils/draft'

import type {
  ChatMessage,
  Citation,
  Conversation,
  ToolCallRecord,
  ToolEvent,
} from '@/modules/chat/api/chat'
import type { KnowledgeBase } from '@/modules/knowledge/api/knowledgeBase'
import type { KnowledgeCard } from '@/modules/publishing/api/public'

interface ChatItem {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
  /** 本次流式过程中的工具过程事件（start/done 时序渲染）。 */
  tools: ToolEvent[]
  /** 历史回放的工具调用记录（来源 toolCallsJson）。 */
  toolCalls: ToolCallRecord[]
  /** 本次回答下方的追问建议（后端 followups 事件）。 */
  followups: string[]
  /** 本条回答关联的用户问题（存为知识草稿时作标题）。 */
  question: string
  streaming: boolean
}

/** 多文档对比：单次最多勾选数量。 */
const COMPARE_LIMIT = 10

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

// 多文档对比：勾选可见知识（上限 COMPARE_LIMIT 篇），确认后随本轮提问传给检索
const compareDialogVisible = ref(false)
const compareLoading = ref(false)
const compareKeyword = ref('')
const compareOptions = ref<KnowledgeCard[]>([])
const compareSelection = ref<string[]>([])
const selectedKnowledgeIds = ref<string[]>([])

// B00 右侧「本轮过程」检查器：取最近一条助手消息的工具轨迹（名称/状态/摘要/耗时）与引用数量。
const currentAssistant = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const message = messages.value[i]
    if (message?.role === 'assistant') return message
  }
  return null
})
const processTools = computed(() =>
  currentAssistant.value ? doneTools(currentAssistant.value.tools) : [],
)
const processDuration = computed(() =>
  processTools.value.reduce((sum, tool) => sum + (tool.durationMs ?? 0), 0),
)
const processCitationCount = computed(() => currentAssistant.value?.citations.length ?? 0)

/** 毫秒 → "04.70s" 耗时文案。 */
function formatDuration(ms: number): string {
  return `${(ms / 1000).toFixed(2)}s`
}

const filteredCompareOptions = computed(() => {
  const keyword = compareKeyword.value.trim().toLowerCase()
  if (!keyword) return compareOptions.value
  return compareOptions.value.filter(
    (item) =>
      item.title.toLowerCase().includes(keyword) || item.kbName.toLowerCase().includes(keyword),
  )
})

/** 打开对比面板：已有选项直接复用，首次拉取可见知识一页（50 条）。 */
async function openCompareDialog(): Promise<void> {
  compareDialogVisible.value = true
  compareKeyword.value = ''
  compareSelection.value = [...selectedKnowledgeIds.value]
  if (compareOptions.value.length > 0) return
  compareLoading.value = true
  try {
    const page = await fetchKnowledges({ pageNo: 1, pageSize: 50 })
    compareOptions.value = page.records
  } catch {
    compareOptions.value = []
  } finally {
    compareLoading.value = false
  }
}

function confirmCompare(): void {
  selectedKnowledgeIds.value = [...compareSelection.value]
  compareDialogVisible.value = false
}

function clearCompare(): void {
  selectedKnowledgeIds.value = []
}

function toChatItem(message: ChatMessage): ChatItem {
  return {
    id: message.id,
    role: message.role,
    content: message.content,
    citations: message.citations,
    tools: [],
    toolCalls: message.toolCalls,
    followups: [],
    question: '',
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
    // 历史回放：为每条 assistant 消息关联其前置用户问题（存草稿时的标题来源）
    let lastQuestion = ''
    for (const message of messages.value) {
      if (message.role === 'user') lastQuestion = message.content
      else message.question = lastQuestion
    }
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

/** 会话操作前置校验：流式中或未选中会话时拦截提示（按钮保持可点，不做置灰）。 */
function guardConversationAction(): boolean {
  if (sending.value) {
    ElMessage.info('回复进行中，请稍候')
    return false
  }
  if (!currentId.value) {
    ElMessage.info('当前没有选中的会话')
    return false
  }
  return true
}

/** 清空当前会话全部消息（保留会话条目）。 */
async function clearCurrentConversation(): Promise<void> {
  if (!guardConversationAction()) return
  try {
    await ElMessageBox.confirm('将清空当前会话的全部消息，会话本身保留。', '清空会话', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return // 取消弹窗
  }
  try {
    await clearConversationMessages(currentId.value as string)
    messages.value = []
    scrollToBottom()
    ElMessage.success('已清空当前会话')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清空失败')
  }
}

/** 删除指定会话（连同全部消息，不可恢复）；删的是当前会话时一并清空消息区。 */
async function removeConversation(conversationId: string): Promise<void> {
  if (sending.value) {
    ElMessage.info('回复进行中，请稍候')
    return
  }
  try {
    await ElMessageBox.confirm('删除后该会话及其全部消息将被移除，不可恢复。', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return // 取消弹窗
  }
  try {
    await deleteConversation(conversationId)
    if (currentId.value === conversationId) {
      currentId.value = null
      messages.value = []
    }
    await loadConversations()
    ElMessage.success('已删除会话')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
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
    followups: [],
    question: '',
    streaming: false,
  })
  // 须用 reactive 代理后再入列，onChunk 持有的引用才能触发流式重渲染
  const assistant = reactive<ChatItem>({
    id: `local-${Date.now()}-assistant`,
    role: 'assistant',
    content: '',
    citations: [],
    tools: [],
    toolCalls: [],
    followups: [],
    question: query,
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
        // 多文档对比：勾选了对比文档时限定检索范围
        ...(selectedKnowledgeIds.value.length > 0
          ? { knowledgeIds: selectedKnowledgeIds.value }
          : {}),
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
        onFollowups: (followups) => {
          assistant.followups = followups
          scrollToBottom()
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

/** 追问 chips 点击：以该文本作为新问题发起一轮提问。 */
function sendFromChip(question: string): void {
  draft.value = question
  void send()
}

const savingDraftId = ref<string | null>(null)

/** 将回答正文（含引用来源清单）存为新知识草稿。 */
async function saveAsDraft(message: ChatItem): Promise<void> {
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
          <div
            v-for="conversation in conversations"
            :key="conversation.id"
            role="button"
            tabindex="0"
            class="chat__conversation"
            :class="{ 'chat__conversation--active': conversation.id === currentId }"
            @click="selectConversation(conversation.id)"
            @keydown.enter="selectConversation(conversation.id)"
          >
            <span class="chat__conversation-title"> {{ conversation.title || '未命名对话' }} </span>
            <!-- 条目级删除：hover 显现，stop 防误触选中 -->
            <span
              class="chat__conversation-del"
              title="删除会话"
              @click.stop="removeConversation(conversation.id)"
            >
              <el-icon><Delete /></el-icon>
            </span>
          </div>
        </nav>
      </template>
      <p v-else class="chat__guest-hint">访客模式：单次问答，不保留会话历史。</p>
    </aside>

    <section class="chat__main">
      <div class="chat__toolbar">
        <el-button size="small" :icon="Collection" @click="openCompareDialog">对比文档</el-button>
        <span v-if="selectedKnowledgeIds.length > 0" class="chat__compare-badge">
          已选择对比 {{ selectedKnowledgeIds.length }} 篇
        </span>
        <el-button v-if="selectedKnowledgeIds.length > 0" size="small" text @click="clearCompare"
          >清除</el-button
        >
        <!-- 会话操作（登录可用）：清空保留会话条目，删除按钮在左侧会话条目上 -->
        <div v-if="session.loggedIn" class="chat__toolbar-actions">
          <el-button size="small" text :icon="Brush" @click="clearCurrentConversation">
            清空会话
          </el-button>
        </div>
      </div>
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
          <div
            v-if="message.role === 'assistant'"
            class="chat-message__avatar chat-message__avatar--ai"
            aria-hidden="true"
          >
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
            <span v-if="message.streaming" class="chat-message__cursor" aria-hidden="true">▍</span>
            <!-- 工具过程：进行中状态行（正在检索） -->
            <div
              v-if="message.role === 'assistant' && activeTools(message.tools).length > 0"
              class="chat-message__active-tools"
            >
              <span
                v-for="tool in activeTools(message.tools)"
                :key="`active-${tool.seq}`"
                class="chat-message__tool-line"
              >
                {{
                  tool.name === 'knowledge.search' ? '正在检索知识库…' : `正在调用 ${tool.name}…`
                }}
              </span>
            </div>
            <!-- 工具轨迹：done 面板（流式过程）+ 历史回放 -->
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
                  <span v-if="tool.summary" class="chat-message__tool-summary">{{
                    tool.summary
                  }}</span>
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
            <!-- 追问 chips：本次回答下方可点击的追问建议 -->
            <FollowupChips
              v-if="message.role === 'assistant' && message.followups.length > 0"
              :questions="message.followups"
              :disabled="sending"
              @select="sendFromChip"
            />
            <!-- 存为知识草稿：登录用户可将回答一键转为创作中心草稿 -->
            <div
              v-if="
                session.loggedIn &&
                message.role === 'assistant' &&
                message.content &&
                !message.streaming
              "
              class="chat-message__draft"
            >
              <button
                type="button"
                class="chat-message__draft-btn"
                :disabled="savingDraftId === message.id"
                @click="saveAsDraft(message)"
              >
                {{ savingDraftId === message.id ? '存入中…' : '存为知识草稿' }}
              </button>
            </div>
          </div>
          <div
            v-if="message.role === 'user'"
            class="chat-message__avatar chat-message__avatar--user"
            aria-hidden="true"
          >
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

    <!-- B00 右侧「本轮过程」检查器：贴图五步轨迹卡片（工具名/状态/摘要/耗时/引用），不显示思维链 -->
    <aside class="chat__process">
      <h2 class="chat__process-title">本轮过程</h2>
      <p v-if="!currentAssistant" class="chat__process-empty">
        提问后在这里查看本轮的工具执行过程。
      </p>
      <template v-else>
        <p v-if="processTools.length === 0" class="chat__process-empty">本轮未调用检索与工具。</p>
        <ul v-else class="chat__process-list">
          <li v-for="tool in processTools" :key="`p-${tool.seq}`" class="chat__process-card">
            <div class="chat__process-head">
              <span
                class="chat__process-state"
                :class="{ 'chat__process-state--fail': tool.ok === false }"
              >
                <el-icon>
                  <CircleCloseFilled v-if="tool.ok === false" />
                  <SuccessFilled v-else />
                </el-icon>
              </span>
              <span class="chat__process-name">{{ tool.name }}</span>
              <span v-if="tool.durationMs != null" class="chat__process-time"
                >{{ tool.durationMs }}ms</span
              >
            </div>
            <span
              class="chat__process-status"
              :class="{ 'chat__process-status--fail': tool.ok === false }"
            >
              {{ tool.ok === false ? '失败' : '完成' }}
            </span>
            <p v-if="tool.summary" class="chat__process-summary">{{ tool.summary }}</p>
            <div v-if="processCitationCount > 0" class="chat__process-ref">
              引用 {{ processCitationCount }}
            </div>
          </li>
        </ul>
        <div v-if="processDuration > 0" class="chat__process-total">
          <el-icon class="chat__process-total-icon"><Clock /></el-icon>
          本轮总耗时
          <span class="chat__process-total-val">{{ formatDuration(processDuration) }}</span>
        </div>
      </template>
    </aside>

    <!-- 多文档对比：勾选可见知识，确认后限定本轮提问的检索范围 -->
    <el-dialog
      v-model="compareDialogVisible"
      title="选择对比文档"
      width="560px"
      @closed="compareKeyword = ''"
    >
      <el-input v-model="compareKeyword" placeholder="搜索知识标题 / 知识库…" clearable />
      <div class="compare-list">
        <p v-if="compareLoading" class="compare-list__hint">知识加载中…</p>
        <p v-else-if="compareOptions.length === 0" class="compare-list__hint">暂无公开可见知识</p>
        <p v-else-if="filteredCompareOptions.length === 0" class="compare-list__hint">
          未找到匹配的知识
        </p>
        <el-checkbox-group
          v-else
          v-model="compareSelection"
          :max="COMPARE_LIMIT"
          class="compare-list__group"
        >
          <el-checkbox
            v-for="item in filteredCompareOptions"
            :key="item.id"
            :value="item.id"
            border
            class="compare-list__item"
          >
            <span class="compare-list__title">{{ item.title }}</span>
            <span class="compare-list__kb">{{ item.kbName }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <span class="compare-list__hint"
          >已选 {{ compareSelection.length }}/{{ COMPARE_LIMIT }} 篇</span
        >
        <el-button @click="compareDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="compareSelection.length === 0" @click="confirmCompare"
          >确认对比</el-button
        >
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 280px;
  height: calc(100vh - var(--xl-header-h));
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
  font-size: 18px;
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
  font-size: var(--xl-fs-caption);
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
  font-size: 14px;
}

.chat__conversation {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.chat__conversation:hover {
  background: var(--xl-bg-secondary);
}

.chat__conversation:focus-visible {
  outline: 2px solid var(--xl-color-primary);
  outline-offset: -2px;
}

.chat__conversation-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 条目级删除：hover/选中时显现的垃圾桶小按钮 */
.chat__conversation-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 6px;
  color: var(--xl-text-muted);
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.15s;
}

.chat__conversation:hover .chat__conversation-del,
.chat__conversation--active .chat__conversation-del {
  opacity: 1;
}

.chat__conversation-del:hover {
  background: color-mix(in srgb, var(--xl-color-danger) 12%, transparent);
  color: var(--xl-color-danger);
}

.chat__conversation--active {
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
}

.chat__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  /* 网格项默认 min-height:auto 会随消息变长而撑高，须收敛为 0 才能让内部消息区滚动、输入框固定 */
  min-height: 0;
}

.chat__toolbar {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-2) var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
}

.chat__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.chat__compare-badge {
  padding: 2px 10px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 40%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-ai) 12%, transparent);
  color: var(--xl-color-ai);
  font-size: var(--xl-fs-caption);
}

.chat__messages {
  flex: 1;
  min-height: 0;
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
  font-size: 20px;
  font-weight: 600;
  box-shadow: var(--xl-shadow-md);
}

.chat__empty-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
}

.chat__empty-text {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
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
  font-size: 12px;
  font-weight: 600;
}

.chat-message__avatar--ai {
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
}

.chat-message__avatar--user {
  background: color-mix(in srgb, var(--xl-color-primary) 18%, white);
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-body);
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
  font-size: var(--xl-fs-caption);
  font-weight: 600;
}

.chat-message__text {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
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
  font-size: 14px;
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

/* 工具过程展示：进行中状态行 + done/历史折叠面板 */
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
  font-size: var(--xl-fs-caption);
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
  font-size: var(--xl-fs-caption);
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
  font-size: var(--xl-fs-caption);
}

.chat-message__tool-name {
  font-family: var(--xl-font-mono);
  font-size: 12px;
}

.chat-message__tool-status {
  padding: 1px 6px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--xl-color-success) 14%, transparent);
  color: var(--xl-color-success);
  font-size: 12px;
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
  font-size: 12px;
}

.chat-message__citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

/* 存为知识草稿：助手消息末尾的小按钮（仅登录可见） */
.chat-message__draft {
  margin-top: 10px;
}

.chat-message__draft-btn {
  padding: 3px 10px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  cursor: pointer;
}

.chat-message__draft-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.chat-message__draft-btn:hover:not(:disabled) {
  border-color: var(--xl-color-ai);
  color: var(--xl-color-ai);
}

/* 多文档对比弹窗：可见知识勾选列表 */
.compare-list {
  margin-top: 12px;
  max-height: 320px;
  overflow-y: auto;
}

.compare-list__hint {
  margin: 0;
  padding: 6px 0;
  color: var(--xl-text-muted);
  font-size: 14px;
}

.compare-list__group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.compare-list__item {
  width: 100%;
  margin-right: 0;
}

.compare-list__item :deep(.el-checkbox__label) {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.compare-list__title {
  color: var(--xl-text-primary);
  font-size: 14px;
  overflow-wrap: break-word;
}

.compare-list__kb {
  flex-shrink: 0;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
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
  font-size: var(--xl-fs-body);
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
  background: color-mix(in srgb, var(--xl-color-ai) 85%, black);
  border-color: color-mix(in srgb, var(--xl-color-ai) 85%, black);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--xl-color-ai) 40%, transparent);
}

/* B00 右侧「本轮过程」检查器 */
.chat__process {
  border-left: 1px solid var(--xl-border);
  background: var(--xl-bg-surface);
  padding: var(--xl-space-4);
  overflow-y: auto;
}

.chat__process-title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

.chat__process-empty {
  margin: 0;
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
  line-height: 1.7;
}

.chat__process-list {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.chat__process-card {
  padding: var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius);
  background: var(--xl-bg-surface);
}

.chat__process-head {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
}

.chat__process-state {
  display: inline-flex;
  flex-shrink: 0;
  color: var(--xl-color-success);
  font-size: 18px;
}

.chat__process-state--fail {
  color: var(--xl-color-danger);
}

.chat__process-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--xl-font-mono);
  font-size: var(--xl-fs-caption);
  color: var(--xl-text-primary);
}

.chat__process-time {
  flex-shrink: 0;
  color: var(--xl-text-muted);
  font-size: 12px;
}

.chat__process-status {
  display: inline-block;
  margin-top: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-success) 14%, transparent);
  color: var(--xl-color-success);
  font-size: 12px;
}

.chat__process-status--fail {
  background: color-mix(in srgb, var(--xl-color-danger) 14%, transparent);
  color: var(--xl-color-danger);
}

.chat__process-summary {
  margin: 6px 0 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.chat__process-ref {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--xl-border);
  color: var(--xl-color-ai);
  font-size: 12px;
}

.chat__process-total {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-3);
  padding: var(--xl-space-3);
  border-radius: var(--xl-radius);
  background: var(--xl-bg-page);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
}

.chat__process-total-icon {
  color: var(--xl-text-muted);
}

.chat__process-total-val {
  margin-left: auto;
  font-family: var(--xl-font-mono);
  font-weight: 600;
  color: var(--xl-text-primary);
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

  .chat__process {
    display: none;
  }
}
</style>
