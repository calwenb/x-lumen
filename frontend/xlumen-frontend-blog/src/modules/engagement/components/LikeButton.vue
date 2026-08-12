<script setup lang="ts">
// 点赞按钮（F-0203，B02）：未登录点击引导登录；切换语义，点击后乐观更新，确认后同步父组件计数。
// 注意：乐观更新期间不 emit（避免父组件 props 变化触发 watch 重置本地状态，导致“已赞 0”错乱）；
// 确认/回滚后一次性 emit 同步。
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

import { toggleLike } from '@/modules/engagement/api/engagement'

const props = defineProps<{
  articleId: string
  initial: boolean
  count: number
}>()

const emit = defineEmits<{
  'update:count': [count: number]
}>()

const router = useRouter()
const session = useSessionStore()

const liked = ref(props.initial)
const count = ref(props.count)
const pending = ref(false)

watch(
  () => [props.initial, props.count],
  () => {
    // 详情页重载时同步；pending 期间忽略（乐观更新中的 emit 同步不应重置本地状态）
    if (!pending.value) {
      liked.value = props.initial
      count.value = props.count
    }
  },
)

async function handleClick(): Promise<void> {
  if (!session.loggedIn) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (pending.value) return
  pending.value = true
  const next = !liked.value
  const nextCount = count.value + (next ? 1 : -1)
  // 乐观更新：失败回滚；确认/回滚后统一 emit 同步父组件计数
  liked.value = next
  count.value = nextCount
  try {
    const confirmed = await toggleLike(props.articleId)
    if (confirmed !== next) {
      liked.value = confirmed
      count.value = count.value + (confirmed ? 1 : -1)
    }
    emit('update:count', count.value)
  } catch {
    liked.value = !next
    count.value = props.count
    emit('update:count', props.count)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <button
    type="button"
    class="like-button"
    :class="{ 'like-button--active': liked }"
    :disabled="pending"
    @click="handleClick"
  >
    <span class="like-button__icon" aria-hidden="true">♥</span>
    <span>{{ liked ? '已赞' : '点赞' }} {{ count }}</span>
  </button>
</template>

<style scoped>
.like-button {
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

.like-button:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.like-button--active {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.like-button__icon {
  font-size: 14px;
}
</style>
