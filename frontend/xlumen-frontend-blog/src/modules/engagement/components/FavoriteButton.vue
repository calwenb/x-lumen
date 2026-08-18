<script setup lang="ts">
// 知识收藏按钮（F-0212，B02）：星形 toggle 高亮，以服务端返回布尔为准；
// 乐观更新 + 服务端校正 + 失败回滚（与 ReactionBar 同一模式）；未登录跳登录页。
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Star, StarFilled } from '@element-plus/icons-vue'

import { useSessionStore } from '@/stores/session'

import { toggleFavorite } from '@/modules/engagement/api/engagement'

const props = defineProps<{
  knowledgeId: string
  initial: boolean
  count: number
}>()

const emit = defineEmits<{
  'update:state': [state: { favorited: boolean; count: number }]
}>()

const router = useRouter()
const session = useSessionStore()

const favorited = ref(props.initial)
const count = ref(props.count)
const pending = ref(false)

watch(
  () => props.knowledgeId,
  () => {
    // 切换知识时同步新的初始状态（同篇知识的交互不触发本 watch）
    favorited.value = props.initial
    count.value = props.count
  },
)

async function handleClick(): Promise<void> {
  if (!session.loggedIn) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (pending.value) return
  pending.value = true
  const guess = !favorited.value
  favorited.value = guess
  count.value += guess ? 1 : -1
  try {
    const confirmed = await toggleFavorite(props.knowledgeId)
    if (confirmed !== guess) {
      // 服务端校正：补偿计数差量
      favorited.value = confirmed
      count.value += confirmed ? 1 : -1
    }
  } catch {
    favorited.value = !guess
    count.value += guess ? -1 : 1
  } finally {
    pending.value = false
  }
  emit('update:state', { favorited: favorited.value, count: count.value })
}
</script>

<template>
  <button
    type="button"
    class="favorite-button"
    :class="{ 'favorite-button--active': favorited }"
    :disabled="pending"
    :aria-pressed="favorited"
    @click="handleClick"
  >
    <el-icon class="favorite-button__icon">
      <StarFilled v-if="favorited" />
      <Star v-else />
    </el-icon>
    <span>{{ favorited ? '已收藏' : '收藏' }} {{ count }}</span>
  </button>
</template>

<style scoped>
.favorite-button {
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-1);
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.favorite-button:hover {
  border-color: var(--xl-color-warning);
  color: var(--xl-color-warning);
}

.favorite-button:disabled {
  cursor: default;
  opacity: 0.6;
}

.favorite-button--active {
  border-color: var(--xl-color-warning);
  background: color-mix(in srgb, var(--xl-color-warning) 10%, transparent);
  color: var(--xl-color-warning);
}

.favorite-button__icon {
  font-size: 14px;
}
</style>
