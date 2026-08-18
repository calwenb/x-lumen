<script setup lang="ts">
// 知识赞/踩按钮组（F-0212，B02）：互斥高亮，以服务端返回的 reaction 为准。
// 交互模式沿用原 LikeButton（BUG-8）：点击后乐观更新 -> 服务端校正 -> 失败回滚；
// 未登录点击跳登录页（携带 redirect）。计数变化通过 update:counts 同步父组件。
// 本文件不使用模板字符串拼接 URL（统一由 api/engagement 构造）。
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

import { toggleDislike, toggleLike } from '@/modules/engagement/api/engagement'

import type { Reaction } from '@/modules/engagement/api/engagement'

type MyReaction = Exclude<Reaction, 'NONE'>

const props = defineProps<{
  knowledgeId: string
  initialReaction: MyReaction | null
  likeCount: number
  dislikeCount: number
}>()

const emit = defineEmits<{
  'update:counts': [counts: { likeCount: number; dislikeCount: number }]
}>()

const router = useRouter()
const session = useSessionStore()

const reaction = ref<MyReaction | null>(props.initialReaction)
const likeCount = ref(props.likeCount)
const dislikeCount = ref(props.dislikeCount)
const pending = ref(false)

watch(
  () => props.knowledgeId,
  () => {
    // 切换知识时同步新的初始状态（同篇知识的交互不触发本 watch，沿用 BUG-8 结论）
    reaction.value = props.initialReaction
    likeCount.value = props.likeCount
    dislikeCount.value = props.dislikeCount
  },
)

/** 反应迁移：from 移除旧计数，to 计入新计数（NONE 侧不计数）。 */
function applyTransition(from: MyReaction | null, to: MyReaction | null): void {
  if (from === 'LIKE') likeCount.value -= 1
  else if (from === 'DISLIKE') dislikeCount.value -= 1
  if (to === 'LIKE') likeCount.value += 1
  else if (to === 'DISLIKE') dislikeCount.value += 1
}

/** 切换赞/踩：toggle 语义（已选中则取消；另一侧则切换过来）。 */
async function react(target: MyReaction): Promise<void> {
  if (!session.loggedIn) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (pending.value) return
  pending.value = true
  const original = reaction.value
  const guess = original === target ? null : target
  applyTransition(original, guess)
  reaction.value = guess
  try {
    const result = target === 'LIKE' ? await toggleLike(props.knowledgeId) : await toggleDislike(props.knowledgeId)
    const final = result.reaction === 'NONE' ? null : result.reaction
    if (final !== reaction.value) {
      // 服务端校正：按迁移语义补偿计数差量
      applyTransition(reaction.value, final)
      reaction.value = final
    }
  } catch {
    applyTransition(reaction.value, original)
    reaction.value = original
  } finally {
    pending.value = false
  }
  emit('update:counts', { likeCount: likeCount.value, dislikeCount: dislikeCount.value })
}
</script>

<template>
  <div class="reaction-bar" role="group" aria-label="点赞与点踩">
    <button
      type="button"
      class="reaction-bar__button"
      :class="{ 'reaction-bar__button--liked': reaction === 'LIKE' }"
      :disabled="pending"
      :aria-pressed="reaction === 'LIKE'"
      @click="react('LIKE')"
    >
      <span aria-hidden="true">👍</span>
      <span>{{ reaction === 'LIKE' ? '已赞' : '赞' }} {{ likeCount }}</span>
    </button>
    <button
      type="button"
      class="reaction-bar__button"
      :class="{ 'reaction-bar__button--disliked': reaction === 'DISLIKE' }"
      :disabled="pending"
      :aria-pressed="reaction === 'DISLIKE'"
      @click="react('DISLIKE')"
    >
      <span aria-hidden="true">👎</span>
      <span>踩 {{ dislikeCount }}</span>
    </button>
  </div>
</template>

<style scoped>
.reaction-bar {
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-2);
}

.reaction-bar__button {
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

.reaction-bar__button:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.reaction-bar__button:disabled {
  cursor: default;
  opacity: 0.6;
}

.reaction-bar__button--liked {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.reaction-bar__button--disliked {
  border-color: var(--xl-color-danger);
  background: color-mix(in srgb, var(--xl-color-danger) 8%, transparent);
  color: var(--xl-color-danger);
}
</style>
