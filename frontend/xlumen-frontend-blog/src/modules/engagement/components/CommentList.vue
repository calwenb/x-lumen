<script setup lang="ts">
// 评论区（F-0203，B02）：评论列表 + 发表评论；发表需登录，未登录引导登录页。
// F-0213：每条评论底部提供赞/踩互斥按钮，以服务端返回 reaction 校正并增减本地计数。
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useSessionStore } from '@/stores/session'

import { createComment, fetchComments, toggleCommentDislike, toggleCommentLike } from '@/modules/engagement/api/engagement'

import type { CommentItem } from '@/modules/engagement/api/engagement'

type MyReaction = 'LIKE' | 'DISLIKE'

const props = defineProps<{
  knowledgeId: string
}>()

const emit = defineEmits<{
  'update:count': [count: number]
}>()

const router = useRouter()
const session = useSessionStore()

const comments = ref<CommentItem[]>([])
const loading = ref(true)
const loadError = ref(false)
const draft = ref('')
const submitting = ref(false)
// 正在切换反应的评论 id：请求期间禁用该评论的两个反应按钮，防重复提交
const pendingCommentId = ref<string | null>(null)

/** 相对时间：分钟/小时/天前；时戳缺失（后端未回填）时返回空串，避免 null 当 1970（BUG-010 防御）。 */
function formatTime(iso: string): string {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const page = await fetchComments(props.knowledgeId)
    comments.value = page.records
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  const content = draft.value.trim()
  if (!content) return
  if (!session.loggedIn) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  submitting.value = true
  try {
    const created = await createComment(props.knowledgeId, content)
    comments.value.push(created)
    draft.value = ''
    emit('update:count', comments.value.length)
  } catch {
    // 失败提示：保持草稿，用户可重试
    ElMessage.error('评论失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(load)

/** 评论反应迁移：from 移除旧计数，to 计入新计数（null 侧不计数）。 */
function applyCommentTransition(comment: CommentItem, from: MyReaction | null, to: MyReaction | null): void {
  if (from === 'LIKE') comment.likeCount -= 1
  else if (from === 'DISLIKE') comment.dislikeCount -= 1
  if (to === 'LIKE') comment.likeCount += 1
  else if (to === 'DISLIKE') comment.dislikeCount += 1
}

/** 评论赞/踩（F-0213）：toggle 语义（已选中取消、互斥切换），服务端 reaction 校正。 */
async function react(comment: CommentItem, target: MyReaction): Promise<void> {
  if (!session.loggedIn) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (pendingCommentId.value) return
  pendingCommentId.value = comment.id
  const original = comment.myReaction
  const guess = original === target ? null : target
  applyCommentTransition(comment, original, guess)
  comment.myReaction = guess
  try {
    const result = target === 'LIKE' ? await toggleCommentLike(comment.id) : await toggleCommentDislike(comment.id)
    const final = result.reaction === 'NONE' ? null : result.reaction
    if (final !== comment.myReaction) {
      applyCommentTransition(comment, comment.myReaction, final)
      comment.myReaction = final
    }
  } catch {
    applyCommentTransition(comment, comment.myReaction, original)
    comment.myReaction = original
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    pendingCommentId.value = null
  }
}
</script>

<template>
  <section class="comment-section">
    <h3 class="comment-section__title">评论（{{ comments.length }}）</h3>

    <div v-if="loading" class="comment-section__hint">评论加载中…</div>
    <div v-else-if="loadError" class="comment-section__hint">
      评论加载失败
      <button type="button" class="comment-section__retry" @click="load">重试</button>
    </div>
    <template v-else>
      <p v-if="comments.length === 0" class="comment-section__hint">还没有评论，来抢沙发吧。</p>
      <ul v-else class="comment-list">
        <li v-for="comment in comments" :key="comment.id" class="comment-item">
          <div class="comment-item__head">
            <span class="comment-item__user">{{ comment.userName }}</span>
            <span class="comment-item__time">{{ formatTime(comment.createdAt) }}</span>
          </div>
          <p class="comment-item__content">{{ comment.content }}</p>
          <div class="comment-item__actions">
            <button
              type="button"
              class="comment-reaction"
              :class="{ 'comment-reaction--liked': comment.myReaction === 'LIKE' }"
              :disabled="pendingCommentId === comment.id"
              :aria-pressed="comment.myReaction === 'LIKE'"
              @click="react(comment, 'LIKE')"
            >
              <span aria-hidden="true">👍</span>
              <span>{{ comment.likeCount }}</span>
            </button>
            <button
              type="button"
              class="comment-reaction"
              :class="{ 'comment-reaction--disliked': comment.myReaction === 'DISLIKE' }"
              :disabled="pendingCommentId === comment.id"
              :aria-pressed="comment.myReaction === 'DISLIKE'"
              @click="react(comment, 'DISLIKE')"
            >
              <span aria-hidden="true">👎</span>
              <span>{{ comment.dislikeCount }}</span>
            </button>
          </div>
        </li>
      </ul>
    </template>

    <form class="comment-form" @submit.prevent="submit">
      <textarea
        v-model="draft"
        class="comment-form__input"
        rows="3"
        maxlength="1000"
        placeholder="写下你的评论…"
      />
      <div class="comment-form__actions">
        <span v-if="!session.loggedIn" class="comment-form__tip">登录后即可评论</span>
        <el-button type="primary" native-type="submit" :disabled="submitting || !draft.trim()">
          {{ submitting ? '发表中' : '发表评论' }}
        </el-button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.comment-section {
  margin-top: var(--xl-space-8);
  padding-top: var(--xl-space-6);
  border-top: 1px solid var(--xl-border);
}

.comment-section__title {
  margin: 0 0 var(--xl-space-4);
  font-size: 16px;
  color: var(--xl-text-primary);
}

.comment-section__hint {
  padding: var(--xl-space-6) 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.comment-section__retry {
  margin-left: var(--xl-space-2);
  border: none;
  background: none;
  color: var(--xl-color-primary);
  cursor: pointer;
}

.comment-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.comment-item {
  padding: var(--xl-space-3) 0;
  border-bottom: 1px solid var(--xl-border);
}

.comment-item__head {
  display: flex;
  align-items: baseline;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-1);
}

.comment-item__user {
  color: var(--xl-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.comment-item__time {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.comment-item__content {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: break-word;
}

.comment-item__actions {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-2);
}

.comment-reaction {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: none;
  color: var(--xl-text-muted);
  font-size: 12px;
  cursor: pointer;
}

.comment-reaction:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.comment-reaction:disabled {
  cursor: default;
  opacity: 0.6;
}

.comment-reaction--liked {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.comment-reaction--disliked {
  border-color: var(--xl-color-danger);
  background: color-mix(in srgb, var(--xl-color-danger) 8%, transparent);
  color: var(--xl-color-danger);
}

.comment-form {
  margin-top: var(--xl-space-6);
}

.comment-form__input {
  width: 100%;
  box-sizing: border-box;
  padding: var(--xl-space-3);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
}

.comment-form__input:focus {
  outline: none;
  border-color: var(--xl-color-primary);
}

.comment-form__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--xl-space-2);
}

.comment-form__tip {
  color: var(--xl-text-muted);
  font-size: 13px;
}
</style>
