<script setup lang="ts">
// 点赞按钮（F-0203，B02）：未登录点击引导登录；切换语义，点击后乐观更新，确认后同步父组件计数。
// 状态以服务端 toggleLike 返回为准（BUG-8 修复）：本地 liked/count 仅在 knowledgeId 变化（组件复用到
// 另一篇知识）时重置，避免自身 emit 引发的 props 更新把 liked 打回页面初始值。
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

import { toggleLike } from '@/modules/engagement/api/engagement'

const props = defineProps<{
  knowledgeId: string
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
  () => props.knowledgeId,
  () => {
    // 切换知识时同步新的初始状态（同篇知识的点赞交互不触发本 watch）
    liked.value = props.initial
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
  const next = !liked.value
  const nextCount = count.value + (next ? 1 : -1)
  // 乐观更新：失败回滚；确认/回滚后统一 emit 同步父组件计数（以服务端返回为准）
  liked.value = next
  count.value = nextCount
  try {
    const confirmed = await toggleLike(props.knowledgeId)
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
