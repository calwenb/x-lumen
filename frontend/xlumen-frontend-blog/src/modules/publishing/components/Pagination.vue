<script setup lang="ts">
// 分页控件（B01/B03）：基于 el-pagination，上一页/下一页/页码 + 总数；pageSize 固定由调用方决定。
defineProps<{
  pageNo: number
  pageSize: number
  total: number
}>()

const emit = defineEmits<{
  change: [pageNo: number]
}>()
</script>

<template>
  <nav v-if="total > 0" class="pagination" aria-label="分页">
    <el-pagination
      :current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next, total"
      background
      @current-change="(page: number) => emit('change', page)"
    />
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--xl-space-6);
}
</style>
