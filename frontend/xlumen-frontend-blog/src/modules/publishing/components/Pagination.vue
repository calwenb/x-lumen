<script setup lang="ts">
// 分页控件（B01/B03）：上一页/下一页 + 页码信息；pageSize 固定由调用方决定。
defineProps<{
  pageNo: number
  pageSize: number
  total: number
}>()

const emit = defineEmits<{
  change: [pageNo: number]
}>()

function totalPages(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize))
}
</script>

<template>
  <nav v-if="total > 0" class="pagination" aria-label="分页">
    <button
      type="button"
      class="pagination__button"
      :disabled="pageNo <= 1"
      @click="emit('change', pageNo - 1)"
    >
      上一页
    </button>
    <span class="pagination__info">
      {{ pageNo }} / {{ totalPages(total, pageSize) }}（共 {{ total }} 条）
    </span>
    <button
      type="button"
      class="pagination__button"
      :disabled="pageNo >= totalPages(total, pageSize)"
      @click="emit('change', pageNo + 1)"
    >
      下一页
    </button>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--xl-space-4);
  margin-top: var(--xl-space-6);
}

.pagination__button {
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: 8px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.pagination__button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pagination__button:hover:not(:disabled) {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.pagination__info {
  color: var(--xl-text-muted);
  font-size: 13px;
}
</style>
