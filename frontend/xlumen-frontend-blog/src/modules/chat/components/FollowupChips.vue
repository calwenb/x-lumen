<script setup lang="ts">
// 追问 chips（对话组）：本次回答下方的可点击追问建议，点击以该文本发起新一轮提问。
defineProps<{
  questions: string[]
  /** 发送进行中禁止点击，避免并发提问。 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [question: string]
}>()
</script>

<template>
  <div v-if="questions.length > 0" class="followup-chips">
    <button
      v-for="question in questions"
      :key="question"
      type="button"
      class="followup-chips__item"
      :disabled="disabled"
      @click="emit('select', question)"
    >
      {{ question }}
    </button>
  </div>
</template>

<style scoped>
.followup-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.followup-chips__item {
  padding: 5px 12px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 55%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--xl-color-ai) 8%, transparent);
  color: var(--xl-color-ai);
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
}

.followup-chips__item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.followup-chips__item:hover:not(:disabled) {
  border-color: var(--xl-color-ai);
  background: color-mix(in srgb, var(--xl-color-ai) 16%, transparent);
}
</style>
