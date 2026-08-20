<script setup lang="ts">
// Markdown 编辑器（F-0301，B10）：编辑/预览双栏切换；预览走 publishing 模块渲染工具（markdown-it + DOMPurify XSS 清洗，PRODUCT §10）。
import { ref } from 'vue'

import { renderMarkdown } from '@/modules/publishing/utils/markdown'

const model = defineModel<string>({ required: true })
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ blur: [] }>()

const mode = ref<'edit' | 'preview' | 'split'>('edit')
</script>

<template>
  <div class="markdown-editor">
    <div class="markdown-editor__toolbar">
      <span class="markdown-editor__label">正文（Markdown）</span>
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
    <div
      class="markdown-editor__body"
      :class="{ 'markdown-editor__body--split': mode === 'split' }"
    >
      <textarea
        v-show="mode !== 'preview'"
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
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--xl-border);
  background: var(--xl-bg-secondary);
}

.markdown-editor__label {
  font-size: 13px;
  color: var(--xl-text-secondary);
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

@media (width < 768px) {
  .markdown-editor__body--split {
    grid-template-columns: 1fr;
  }
}
</style>
