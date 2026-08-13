<script setup lang="ts">
// AI 任务进度指示（B11）：按阶段展示提交/生成/完成/失败状态。
type AiTaskStatus = 'submitting' | 'streaming' | 'done' | 'error'

const props = defineProps<{
  status: AiTaskStatus
  error?: string
}>()

const labels: Record<AiTaskStatus, string> = {
  submitting: '正在提交写作任务…',
  streaming: '小光正在写作…',
  done: '生成完成',
  error: '生成失败',
}
</script>

<template>
  <div class="ai-task-progress" role="status">
    <span
      v-if="props.status === 'submitting' || props.status === 'streaming'"
      class="ai-task-progress__spinner"
      aria-hidden="true"
    />
    <span v-else class="ai-task-progress__mark" :class="`ai-task-progress__mark--${props.status}`" aria-hidden="true">
      {{ props.status === 'done' ? '✓' : '✕' }}
    </span>
    <span class="ai-task-progress__text" :class="{ 'ai-task-progress__text--error': props.status === 'error' }">
      {{ props.error ?? labels[props.status] }}
    </span>
  </div>
</template>

<style scoped>
.ai-task-progress {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  padding: var(--xl-space-3) var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
}

.ai-task-progress__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--xl-border);
  border-top-color: var(--xl-color-ai);
  border-radius: 50%;
  animation: ai-task-progress-spin 0.8s linear infinite;
}

@keyframes ai-task-progress-spin {
  to {
    transform: rotate(360deg);
  }
}

.ai-task-progress__mark {
  font-size: 14px;
  line-height: 1;
}

.ai-task-progress__mark--done {
  color: var(--xl-color-ai);
}

.ai-task-progress__mark--error {
  color: var(--xl-color-danger, #d03050);
}

.ai-task-progress__text {
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.ai-task-progress__text--error {
  color: var(--xl-color-danger, #d03050);
}
</style>
