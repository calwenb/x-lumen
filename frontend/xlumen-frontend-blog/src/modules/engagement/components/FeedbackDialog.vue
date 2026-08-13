<script setup lang="ts">
// 读者纠错弹窗（M11，F-1001）：问题必填、位置/证据选填；匿名提交后展示追踪编号。
// 详情页集成点（详情页文件不在本次改动范围）。
import { ref } from 'vue'

import { submitFeedback } from '@/modules/engagement/api/feedback'

const props = defineProps<{
  articleId: string
}>()

const emit = defineEmits<{
  close: []
}>()

const problem = ref('')
const position = ref('')
const evidence = ref('')
const submitting = ref(false)
const error = ref('')
const trackNo = ref('')

async function submit(): Promise<void> {
  const value = problem.value.trim()
  if (!value) {
    error.value = '请填写问题描述'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const result = await submitFeedback(props.articleId, {
      problem: value,
      ...(position.value.trim() ? { position: position.value.trim() } : {}),
      ...(evidence.value.trim() ? { evidence: evidence.value.trim() } : {}),
    })
    trackNo.value = result.trackNo
  } catch (err) {
    error.value = err instanceof Error ? err.message : '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="feedback-dialog__overlay" @click.self="emit('close')">
    <div class="feedback-dialog" role="dialog" aria-modal="true" aria-label="读者纠错">
      <header class="feedback-dialog__header">
        <h2 class="feedback-dialog__title">读者纠错</h2>
        <button type="button" class="feedback-dialog__close" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <form v-if="!trackNo" class="feedback-dialog__form" @submit.prevent="submit">
        <label class="feedback-dialog__field">
          <span class="feedback-dialog__label">问题描述 *</span>
          <textarea v-model="problem" class="feedback-dialog__textarea" rows="3" placeholder="请描述你发现的问题，如错别字、事实错误、链接失效…" />
        </label>
        <label class="feedback-dialog__field">
          <span class="feedback-dialog__label">位置（可选）</span>
          <input v-model="position" class="feedback-dialog__input" type="text" placeholder="例如：第二节第 3 段" />
        </label>
        <label class="feedback-dialog__field">
          <span class="feedback-dialog__label">证据（可选）</span>
          <textarea v-model="evidence" class="feedback-dialog__textarea" rows="2" placeholder="补充佐证链接或原文摘录…" />
        </label>
        <p v-if="error" class="feedback-dialog__error" role="alert">{{ error }}</p>
        <div class="feedback-dialog__actions">
          <button type="button" class="feedback-dialog__cancel" @click="emit('close')">取消</button>
          <button type="submit" class="feedback-dialog__submit" :disabled="submitting || !problem.trim()">
            {{ submitting ? '提交中…' : '提交' }}
          </button>
        </div>
      </form>

      <div v-else class="feedback-dialog__done">
        <p class="feedback-dialog__done-title">感谢你的反馈</p>
        <p class="feedback-dialog__done-text">问题已受理，追踪编号：</p>
        <p class="feedback-dialog__track-no">{{ trackNo }}</p>
        <button type="button" class="feedback-dialog__submit" @click="emit('close')">关闭</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feedback-dialog__overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--xl-space-4);
  background: color-mix(in srgb, var(--xl-text-primary) 40%, transparent);
}

.feedback-dialog {
  width: 100%;
  max-width: 480px;
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  overflow: hidden;
}

.feedback-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
}

.feedback-dialog__title {
  margin: 0;
  font-size: 16px;
}

.feedback-dialog__close {
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.feedback-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--xl-space-4);
}

.feedback-dialog__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.feedback-dialog__label {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.feedback-dialog__input,
.feedback-dialog__textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-page);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 13px;
  outline: none;
}

.feedback-dialog__textarea {
  resize: vertical;
}

.feedback-dialog__input:focus,
.feedback-dialog__textarea:focus {
  border-color: var(--xl-color-primary);
}

.feedback-dialog__error {
  margin: 0;
  color: var(--xl-color-danger, #d03050);
  font-size: 13px;
}

.feedback-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.feedback-dialog__cancel {
  padding: 7px 16px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: transparent;
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.feedback-dialog__submit {
  padding: 7px 16px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.feedback-dialog__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.feedback-dialog__submit:hover:not(:disabled) {
  background: var(--xl-color-primary-hover);
}

.feedback-dialog__done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: var(--xl-space-6) var(--xl-space-4);
  text-align: center;
}

.feedback-dialog__done-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.feedback-dialog__done-text {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.feedback-dialog__track-no {
  margin: 0 0 8px;
  color: var(--xl-color-primary);
  font-size: 18px;
  font-weight: 600;
}
</style>
