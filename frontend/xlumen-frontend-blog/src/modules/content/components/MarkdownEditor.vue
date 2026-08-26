<script setup lang="ts">
// Markdown 编辑器（B10）：编辑/预览双栏切换；预览走 publishing 模块渲染工具（markdown-it + DOMPurify XSS 清洗）。
// AI 辅助工具条：续写/润色/标题/错别字，走统一交互端点 /ai/assist；结果写回正文，
// 改动经 update:model-value 触发父级自动保存感知，不干扰键盘/滚动等既有编辑行为。
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import AiSparkles from '@/modules/ai/components/AiSparkles.vue'
import { assistAction } from '@/modules/ai/api/assist'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'

import type { AssistAction, AssistRequest } from '@/modules/ai/api/assist'

const model = defineModel<string>({ required: true })
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ blur: [] }>()

const mode = ref<'edit' | 'preview' | 'split'>('edit')
const inputRef = ref<HTMLTextAreaElement | null>(null)

/** AI 工具条动作定义（顺序即展示顺序）。 */
const AI_ACTIONS: ReadonlyArray<{ action: AssistAction; label: string }> = [
  { action: 'continue', label: '续写' },
  { action: 'polish', label: '润色' },
  { action: 'titles', label: '标题' },
  { action: 'spellfix', label: '错别字' },
]

const aiLoading = ref<Partial<Record<AssistAction, boolean>>>({})
const aiBusy = computed(() => AI_ACTIONS.some((item) => aiLoading.value[item.action] === true))

/** 标题生成结果与选择弹窗。 */
const titleCandidates = ref<string[]>([])
const showTitlePicker = ref(false)

/** 读取当前文本域选区内容（无选区返回空串）。 */
function readSelection(): string {
  const textarea = inputRef.value
  if (!textarea) return ''
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  if (start == null || end == null || end <= start) return ''
  return model.value.slice(start, end).trim()
}

async function runAssist(action: AssistAction): Promise<void> {
  if (!model.value.trim()) {
    ElMessage.warning('正文内容为空，请先输入内容再使用 AI 辅助')
    return
  }
  const content = model.value
  const selection = readSelection()
  const body: AssistRequest = { action, content }
  if (selection) {
    body.selection = selection
  }
  aiLoading.value[action] = true
  try {
    const text = await assistAction(body)
    applyResult(action, text)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 辅助失败，请稍后重试')
  } finally {
    aiLoading.value[action] = false
  }
}

/** 按动作应用结果：续写=末尾追加；标题=展示候选；润色/错别字=整篇替换。 */
function applyResult(action: AssistAction, text: string): void {
  if (action === 'continue') {
    if (!text.trim()) return
    const base = model.value.replace(/\s+$/, '')
    model.value = base ? `${base}\n\n${text}` : text
  } else if (action === 'titles') {
    titleCandidates.value = parseTitleLines(text)
    showTitlePicker.value = true
  } else if (text) {
    model.value = text
  }
}

/** 解析标题候选：逐行拆分、过滤空行、按序去重。 */
function parseTitleLines(text: string): string[] {
  const seen = new Set<string>()
  const lines: string[] = []
  for (const line of text.split(/\r?\n/)) {
    const candidate = line.trim()
    if (candidate && !seen.has(candidate)) {
      seen.add(candidate)
      lines.push(candidate)
    }
  }
  return lines
}

/** 插入标题：正文首行为 Markdown 标题时替换之，否则作为一级标题前置。 */
function applyTitle(candidate: string): void {
  const title = candidate.trim()
  if (!title) return
  const current = model.value.replace(/^\s+/, '')
  const match = /^(#+)\s+[^\n]*/.exec(current)
  if (match && match[0] != null) {
    model.value = current.replace(match[0], `# ${title}`)
  } else {
    model.value = `# ${title}\n\n${current}`
  }
  showTitlePicker.value = false
  ElMessage.success('标题已插入正文开头')
}

/** 复制标题候选到剪贴板。 */
async function copyTitle(candidate: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(candidate)
    ElMessage.success('标题已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}
</script>

<template>
  <div class="markdown-editor">
    <div class="markdown-editor__toolbar">
      <span class="markdown-editor__label">正文（Markdown）</span>
      <div class="markdown-editor__actions">
        <div class="markdown-editor__ai" role="group" aria-label="AI 辅助">
          <span class="markdown-editor__ai-badge">
            <AiSparkles class="markdown-editor__ai-badge-icon" />
            AI
          </span>
          <el-button
            v-for="item in AI_ACTIONS"
            :key="item.action"
            size="small"
            class="markdown-editor__ai-btn"
            :loading="aiLoading[item.action] === true"
            :disabled="disabled || aiBusy"
            @click="runAssist(item.action)"
          >
            {{ item.label }}
          </el-button>
        </div>
        <div class="markdown-editor__modes" role="tablist" aria-label="编辑模式">
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'edit'"
            class="markdown-editor__mode"
            :class="{ 'markdown-editor__mode--active': mode === 'edit' }"
            :disabled="disabled"
            @click="mode = 'edit'"
          >
            编辑
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'split'"
            class="markdown-editor__mode"
            :class="{ 'markdown-editor__mode--active': mode === 'split' }"
            :disabled="disabled"
            @click="mode = 'split'"
          >
            双栏
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'preview'"
            class="markdown-editor__mode"
            :class="{ 'markdown-editor__mode--active': mode === 'preview' }"
            :disabled="disabled"
            @click="mode = 'preview'"
          >
            预览
          </button>
        </div>
      </div>
    </div>
    <div
      class="markdown-editor__body"
      :class="{ 'markdown-editor__body--split': mode === 'split' }"
    >
      <textarea
        v-show="mode !== 'preview'"
        ref="inputRef"
        v-model="model"
        class="markdown-editor__input"
        placeholder="支持 Markdown 语法：标题、列表、代码块、链接……"
        aria-label="正文编辑区"
        :disabled="disabled"
        @blur="emit('blur')"
      />
      <div
        v-show="mode !== 'edit'"
        class="markdown-editor__preview markdown-body"
        v-html="renderMarkdown(model)"
      />
    </div>

    <el-dialog
      v-model="showTitlePicker"
      title="AI 标题建议"
      width="min(560px, 92vw)"
      append-to-body
    >
      <p v-if="titleCandidates.length === 0" class="markdown-editor__titles-empty">
        未解析到候选标题，请重试。
      </p>
      <ul v-else class="markdown-editor__titles">
        <li
          v-for="(title, index) in titleCandidates"
          :key="`${title}-${index}`"
          class="markdown-editor__title"
        >
          <span class="markdown-editor__title-text">{{ title }}</span>
          <div class="markdown-editor__title-actions">
            <el-button
              size="small"
              :type="index === 0 ? 'primary' : 'default'"
              @click="applyTitle(title)"
            >
              插入
            </el-button>
            <el-button size="small" plain @click="copyTitle(title)">复制</el-button>
          </div>
        </li>
      </ul>
    </el-dialog>
  </div>
</template>

<style scoped>
.markdown-editor {
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow: hidden;
}

.markdown-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--xl-border);
  background: var(--xl-bg-secondary);
}

.markdown-editor__label {
  font-size: 13px;
  color: var(--xl-text-secondary);
}

.markdown-editor__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.markdown-editor__ai {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* AI 能力区：AI 品牌色点缀（角标 + 按钮浅色描边），不做重装饰 */
.markdown-editor__ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 40%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-ai) 8%, transparent);
  color: var(--xl-color-ai);
  font-size: 12px;
  font-weight: 600;
}

.markdown-editor__ai-badge-icon {
  font-size: 12px;
}

.markdown-editor__ai-btn {
  --el-button-bg-color: transparent;
  --el-button-border-color: color-mix(in srgb, var(--xl-color-ai) 35%, transparent);
  --el-button-text-color: var(--xl-color-ai);
  --el-button-hover-bg-color: color-mix(in srgb, var(--xl-color-ai) 10%, transparent);
  --el-button-hover-border-color: var(--xl-color-ai);
  --el-button-hover-text-color: var(--xl-color-ai);
  --el-button-active-bg-color: color-mix(in srgb, var(--xl-color-ai) 14%, transparent);
  --el-button-active-border-color: var(--xl-color-ai);
}

.markdown-editor__modes {
  display: flex;
  gap: 4px;
}

.markdown-editor__mode {
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: var(--xl-radius-sm, 6px);
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.markdown-editor__mode--active {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.markdown-editor__mode:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.markdown-editor__body {
  display: grid;
  grid-template-columns: 1fr;
  min-height: 420px;
}

.markdown-editor__body--split {
  grid-template-columns: 1fr 1fr;
}

.markdown-editor__input {
  width: 100%;
  min-height: 420px;
  padding: 14px;
  border: none;
  resize: vertical;
  background: transparent;
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  outline: none;
}

.markdown-editor__input:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.markdown-editor__preview {
  min-height: 420px;
  padding: 14px;
  border-left: 1px solid var(--xl-border);
  overflow: auto;
}

.markdown-editor__body--split .markdown-editor__input {
  border-right: 1px solid var(--xl-border);
}

.markdown-editor__body--split .markdown-editor__preview {
  border-left: none;
}

.markdown-editor__titles {
  margin: 0;
  padding: 0;
  list-style: none;
}

.markdown-editor__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--xl-border);
}

.markdown-editor__title:last-child {
  border-bottom: none;
}

.markdown-editor__title-text {
  min-width: 0;
  color: var(--xl-text-primary);
  font-size: 14px;
  line-height: 1.5;
  overflow-wrap: break-word;
}

.markdown-editor__title-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.markdown-editor__titles-empty {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

@media (width < 768px) {
  .markdown-editor__body--split {
    grid-template-columns: 1fr;
  }
}
</style>
