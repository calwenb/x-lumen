<script setup lang="ts">
// 评论区（B02）：评论列表 + 发表评论；发表需登录，未登录引导登录页。
// 每条评论底部提供赞/踩互斥按钮，以服务端返回 reaction 校正并增减本地计数。
// @小光 的评论：小光回复在发表请求内同步落库（后端 @EventListener 同线程），提交后静默重拉
// 列表即可同时呈现本人评论与回复，无需刷新页面；普通评论仍走本地 push 避免整表闪烁。
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useSessionStore } from '@/stores/session'

import {
  createComment,
  fetchComments,
  toggleCommentDislike,
  toggleCommentLike,
} from '@/modules/engagement/api/engagement'

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

/** 相对时间：分钟/小时/天前；时戳缺失（后端未回填）时返回空串，避免 null 当 1970。 */
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

/** @小光 触发判定：口径对齐后端 CommentServiceImpl（半角/全角 @ 均可命中）。 */
const MENTION_XIAOGUANG = /[@＠]小光/

/** 拉取评论列表；silent 模式不闪 loading 占位、失败不翻错误态（发表后的增量刷新用）。 */
async function loadComments(silent = false): Promise<void> {
  if (!silent) loading.value = true
  loadError.value = false
  try {
    const page = await fetchComments(props.knowledgeId)
    comments.value = page.records
  } catch {
    if (!silent) loadError.value = true
  } finally {
    if (!silent) loading.value = false
  }
}

function load(): void {
  void loadComments()
}

async function submit(): Promise<void> {
  // 按钮不再做禁用态（避免「灰死像锁住」的观感），空内容/提交中在此拦截
  if (submitting.value) return
  const content = draft.value.trim()
  if (!content) {
    ElMessage.warning('请先输入评论内容')
    return
  }
  if (!session.loggedIn) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  submitting.value = true
  try {
    const created = await createComment(props.knowledgeId, content)
    draft.value = ''
    if (MENTION_XIAOGUANG.test(content)) {
      // 回复与本人评论同请求落库，静默重拉一次即两者齐现。
      // 重拉失败或列表分页未含本条时本地补入本人评论，效果退化为普通评论路径
      await loadComments(true)
      if (!comments.value.some((item) => item.id === created.id)) comments.value.push(created)
      // 限流命中（同一用户对同一知识 5 分钟仅回复一次）时后端静默跳过，这里给出明确反馈避免「无响应」观感
      const hasReply = comments.value.some((item) => item.isAi && item.parentId === created.id)
      if (!hasReply) {
        ElMessage.info('小光这次没有回复：同一文章它每 5 分钟只解答一次，可稍后再试')
      }
    } else {
      comments.value.push(created)
    }
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
function applyCommentTransition(
  comment: CommentItem,
  from: MyReaction | null,
  to: MyReaction | null,
): void {
  if (from === 'LIKE') comment.likeCount -= 1
  else if (from === 'DISLIKE') comment.dislikeCount -= 1
  if (to === 'LIKE') comment.likeCount += 1
  else if (to === 'DISLIKE') comment.dislikeCount += 1
}

/** 评论赞/踩：toggle 语义（已选中取消、互斥切换），服务端 reaction 校正。 */
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
    const result =
      target === 'LIKE'
        ? await toggleCommentLike(comment.id)
        : await toggleCommentDislike(comment.id)
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
            <span class="comment-item__user">
              {{ comment.userName }}
              <span v-if="comment.isAi" class="comment-item__ai-badge">小光 AI 回复</span>
            </span>
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
        placeholder="写下你的评论…（可 @小光 让 AI 助理解答）"
      />
      <div class="comment-form__actions">
        <p v-if="session.loggedIn" class="comment-form__tip">
          想让小光解答？评论中带上
          <strong class="comment-form__mention">@小光</strong>
          ，它会基于站内知识回复
        </p>
        <span v-else class="comment-form__tip">登录后即可评论，评论中可 @小光 唤 AI 助理解答</span>
        <el-button type="primary" native-type="submit">
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
  font-size: 18px;
  color: var(--xl-text-primary);
}

.comment-section__hint {
  padding: var(--xl-space-6) 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
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
  display: inline-flex;
  align-items: center;
  gap: var(--xl-space-2);
  color: var(--xl-color-primary);
  font-size: 14px;
  font-weight: 600;
}

/* 「小光 AI 回复」凹形小徽标（AI 色） */
.comment-item__ai-badge {
  padding: 1px 8px;
  border: 1px solid color-mix(in srgb, var(--xl-color-ai) 45%, transparent);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  box-shadow: inset 0 1px 2px color-mix(in srgb, var(--xl-color-ai) 18%, transparent);
  color: var(--xl-color-ai);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
}

.comment-item__time {
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.comment-item__content {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
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
  font-size: var(--xl-fs-caption);
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
  font-size: var(--xl-fs-body);
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
  margin: 0;
  color: var(--xl-text-muted);
  font-size: 14px;
}

.comment-form__mention {
  color: var(--xl-color-ai);
  font-weight: 600;
}
</style>
