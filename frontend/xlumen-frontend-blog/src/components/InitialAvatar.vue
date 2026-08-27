<script setup lang="ts">
// 首字圆形头像（V2 设计系统）：作者/发布者姓名首字，不暗示头像上传功能（FRONTEND.md §10）。
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 姓名或用户名 */
    name?: string
    /** 直径（px） */
    size?: number
  }>(),
  { name: '', size: 28 },
)

const initial = computed(() => (props.name ? props.name.trim().slice(0, 1).toUpperCase() : '?'))
</script>

<template>
  <span
    class="initial-avatar"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.42)}px` }"
    aria-hidden="true"
  >
    {{ initial }}
  </span>
</template>

<style scoped>
.initial-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--xl-color-primary), var(--xl-color-ai));
  color: #fff;
  font-weight: 600;
  letter-spacing: 0;
  user-select: none;
}
</style>
