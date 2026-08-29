<script setup lang="ts">
// 消息铃铛：登录态顶栏组件——未读角标（30s 轮询）+ 下拉消息列表（最近 20 条）。
// 消息点击：标记已读并跳转 link（审核结果 → /studio/review 审核中心）。
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Check } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'

import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  streamNotifications,
} from '@/modules/notification/api/notification'

import type { NotificationItem } from '@/modules/notification/api/notification'

const router = useRouter()

const unreadCount = ref(0)
const items = ref<NotificationItem[]>([])
const loading = ref(false)
const popoverVisible = ref(false)

let timer: number | undefined
/** 实时推送流控制器（卸载时断开）。 */
let streamController: AbortController | null = null
let streamStopped = false

/** 下拉开启时拉取列表，平时只轮询未读数（列表为空时也补拉一次）。 */
async function refresh(): Promise<void> {
  try {
    if (!items.value.length || popoverVisible.value) {
      const page = await fetchNotifications(1, 20)
      items.value = page.records
      unreadCount.value = Number(page.unreadCount)
    } else {
      unreadCount.value = Number(await fetchUnreadCount())
    }
  } catch {
    // 消息加载失败不阻断页面
  }
}

async function openPanel(): Promise<void> {
  popoverVisible.value = true
  loading.value = true
  try {
    const page = await fetchNotifications(1, 20)
    items.value = page.records
    unreadCount.value = Number(page.unreadCount)
  } catch {
    // 忽略
  } finally {
    loading.value = false
  }
}

async function openNotification(item: NotificationItem): Promise<void> {
  if (!item.read) {
    void markNotificationRead(item.id).catch(() => undefined)
    item.read = true
    if (unreadCount.value > 0) unreadCount.value -= 1
  }
  popoverVisible.value = false
  if (item.link) {
    await router.push(item.link)
  } else {
    void router.push({ name: 'workbench' })
  }
}

async function markAllRead(): Promise<void> {
  try {
    await markAllNotificationsRead()
    unreadCount.value = 0
    items.value = items.value.map((item) => ({ ...item, read: true }))
  } catch {
    // 忽略
  }
}

/** 相对时间（分钟/小时/天）简化展示。 */
function timeAgo(iso: string): string {
  if (!iso) return ''
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return ''
  const diff = Date.now() - time
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

/** 通知类型 → 弹窗颜色：未通过/失败=error，通过=success，其余 info。 */
function notificationType(title: string): 'success' | 'info' | 'warning' | 'error' {
  if (title.includes('未通过') || title.includes('失败')) return 'error'
  if (title.includes('通过')) return 'success'
  return 'info'
}

/** 实时推送到达：入列 + 未读数 +1 + 右上角弹窗（点击跳转），断线期间由 30s 轮询兜底。 */
function handleStreamItem(item: NotificationItem): void {
  items.value = [item, ...items.value.filter((i) => i.id !== item.id)].slice(0, 30)
  if (!item.read) unreadCount.value += 1
  ElNotification({
    title: item.title,
    message: item.content,
    type: notificationType(item.title),
    duration: 8000,
    onClick: () => void openNotification(item),
  })
}

/** 实时推送流（SSE）+ 断线自动重连。 */
async function openStream(): Promise<void> {
  streamController = new AbortController()
  while (!streamStopped) {
    try {
      await streamNotifications(handleStreamItem, streamController.signal)
    } catch {
      // 断线/鉴权失败：稍后重连
    }
    if (streamStopped) break
    await new Promise((resolve) => window.setTimeout(resolve, 5000))
  }
}

/** 未读消息取前 3 条内容做角标文案。 */
function unreadPreview(): string {
  const unread = items.value.filter((item) => !item.read)
  return (
    unread
      .slice(0, 3)
      .map((item) => item.content)
      .join('；') || ''
  )
}

onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
  streamStopped = true
  streamController?.abort()
})

void refresh()
void openStream()
timer = window.setInterval(() => void refresh(), 30_000)
</script>

<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="340"
    trigger="click"
    :show-arrow="false"
    @show="openPanel"
  >
    <template #reference>
      <el-badge
        :value="Number(unreadCount)"
        :hidden="Number(unreadCount) === 0"
        :max="99"
        class="noti-bell__badge"
      >
        <button
          type="button"
          class="noti-bell"
          aria-label="消息中心"
          :title="unreadPreview() || '消息中心'"
        >
          <el-icon><Bell /></el-icon>
        </button>
      </el-badge>
    </template>

    <div class="noti-panel">
      <header class="noti-panel__header">
        <span class="noti-panel__title">消息</span>
        <el-button
          v-if="unreadCount > 0"
          link
          type="primary"
          size="small"
          :icon="Check"
          @click="markAllRead"
        >
          全部已读
        </el-button>
      </header>
      <div v-if="loading" class="noti-panel__hint">加载中…</div>
      <div v-else-if="items.length === 0" class="noti-panel__hint">暂无消息</div>
      <ul v-else class="noti-panel__list">
        <li v-for="item in items" :key="item.id">
          <button
            type="button"
            class="noti-item"
            :class="{ 'noti-item--unread': !item.read }"
            @click="openNotification(item)"
          >
            <span class="noti-item__dot" aria-hidden="true" />
            <span class="noti-item__body">
              <span class="noti-item__title">{{ item.title }}</span>
              <span class="noti-item__content">{{ item.content }}</span>
              <span class="noti-item__time">{{ timeAgo(item.createdAt) }}</span>
            </span>
          </button>
        </li>
      </ul>
      <footer class="noti-panel__footer">
        <RouterLink class="noti-panel__more" :to="{ name: 'review-center' }"
          >查看审核中心 →</RouterLink
        >
      </footer>
    </div>
  </el-popover>
</template>

<style scoped>
.noti-bell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 18px;
  cursor: pointer;
}

.noti-bell:hover {
  background: var(--xl-bg-secondary);
  color: var(--xl-color-primary);
}

.noti-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.noti-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--xl-border);
}

.noti-panel__title {
  font-size: var(--xl-fs-body);
  font-weight: 600;
}

.noti-panel__hint {
  padding: 24px 0;
  text-align: center;
  color: var(--xl-text-muted);
  font-size: 14px;
}

.noti-panel__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 320px;
  overflow-y: auto;
}

.noti-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 8px;
  border: none;
  border-radius: 8px;
  background: none;
  text-align: left;
  cursor: pointer;
}

.noti-item:hover {
  background: var(--xl-bg-secondary);
}

.noti-item__dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  margin-top: 5px;
  border-radius: 50%;
  background: transparent;
}

.noti-item--unread .noti-item__dot {
  background: var(--xl-color-primary);
}

.noti-item__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.noti-item__title {
  color: var(--xl-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.noti-item__content {
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.noti-item__time {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.noti-panel__footer {
  padding-top: 8px;
  border-top: 1px solid var(--xl-border);
  text-align: center;
}

.noti-panel__more {
  color: var(--xl-color-primary);
  font-size: 14px;
  text-decoration: none;
}
</style>
