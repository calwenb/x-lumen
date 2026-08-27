<script setup lang="ts">
// 分段控制（V2 设计系统）：关键词 / 向量语义 / 问小光 三态切换（FRONTEND.md §10）。
// 纯 UI 组件，选中项通过 v-model 双向绑定。
defineProps<{
  /** 选项列表 */
  options: ReadonlyArray<{ label: string; value: string }>
  modelValue: string
  /** 底部分段激活下划线颜色，默认 Indigo（AI 项可用 AI Teal） */
  activeColor?: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

function pick(v: string): void {
  emit('update:modelValue', v)
}
</script>

<template>
  <div class="segmented" role="tablist">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="segmented__item"
      :class="{ 'is-active': opt.value === modelValue }"
      :style="opt.value === modelValue && activeColor ? { '--seg-active': activeColor } : undefined"
      role="tab"
      :aria-selected="opt.value === modelValue"
      @click="pick(opt.value)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

<style scoped>
.segmented {
  display: inline-flex;
  gap: var(--xl-space-4);
  border-bottom: 1px solid var(--xl-border);
}

.segmented__item {
  position: relative;
  padding: 8px 2px;
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-family: var(--xl-font-sans);
  font-size: var(--xl-fs-body);
  line-height: 1;
  cursor: pointer;
  transition:
    color var(--xl-transition),
    border-color var(--xl-transition);
}

.segmented__item:hover {
  color: var(--xl-color-primary);
}

.segmented__item.is-active {
  color: var(--xl-color-primary);
  font-weight: var(--xl-fs-title-w);
}

.segmented__item.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 999px;
  background: var(--seg-active, var(--xl-color-primary));
}
</style>
