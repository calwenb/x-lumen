<script setup lang="ts">
// 站点 AI 导游：首次访问（localStorage xlumen.tour.v1 不存在）且不在登录/注册页时，
// 自动弹出自绘的轻量分步导览（无第三方库）：欢迎 → 浏览知识与详情 → 搜索与 AI 问答 → 创作与收藏。
// 「跳过」与「开始使用」都视为已看过，写入 localStorage 后不再弹出。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const STORAGE_KEY = 'xlumen.tour.v1'

interface TourStep {
  title: string
  description: string
}

const STEPS: TourStep[] = [
  {
    title: '欢迎来到 xLumen',
    description: '这里既是专注阅读的博客，也是 AI 创作与知识对话的平台。四步带你快速上手核心功能。',
  },
  {
    title: '浏览知识与详情',
    description:
      '首页「知识」聚合最新发布的内容，点击进入详情可阅读正文、用目录跳转，并查看 AI 摘要与导读。',
  },
  {
    title: '搜索与 AI 问答',
    description:
      '顶栏搜索框支持关键词与标签检索；阅读时随时可以点击「问小光」，就当前文章内容向 AI 提问。',
  },
  {
    title: '创作与收藏',
    description:
      '登录后可从顶栏进入创作中心撰写新知识，把感兴趣的文章加入收藏，在「我的收藏」中随时回顾。',
  },
]

const route = useRoute()
const visible = ref(false)
const stepIndex = ref(0)

const current = computed(() => {
  const step = STEPS[stepIndex.value]
  return step ?? STEPS[0] ?? { title: '', description: '' }
})

const isLastStep = computed(() => stepIndex.value === STEPS.length - 1)

/** 已观看标记：跳过与走完最后一步都写入，保证只引导一次。 */
function markSeen(): void {
  try {
    localStorage.setItem(STORAGE_KEY, '1')
  } catch {
    // 隐私模式等写入失败：忽略，本次会话靠内存状态不再弹出
  }
  visible.value = false
}

function next(): void {
  if (isLastStep.value) {
    markSeen()
    return
  }
  stepIndex.value += 1
}

function prev(): void {
  if (stepIndex.value > 0) stepIndex.value -= 1
}

function skip(): void {
  markSeen()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') skip()
}

onMounted(() => {
  let done = false
  try {
    done = localStorage.getItem(STORAGE_KEY) !== null
  } catch {
    // 存储不可用视为未看过，但弹窗本身不依赖存储
  }
  if (done) return
  if (route.path === '/login' || route.path === '/register') return
  visible.value = true
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="tour">
      <div class="tour__overlay" aria-hidden="true" />
      <div class="tour__card" role="dialog" aria-modal="true" aria-label="站点导览">
        <span class="tour__steps">{{ stepIndex + 1 }} / {{ STEPS.length }}</span>
        <button type="button" class="tour__close" aria-label="关闭导览" @click="skip">×</button>
        <h2 class="tour__title">{{ current.title }}</h2>
        <p class="tour__desc">{{ current.description }}</p>
        <div class="tour__dots" aria-hidden="true">
          <span
            v-for="index in STEPS.length"
            :key="index"
            class="tour__dot"
            :class="{ 'tour__dot--active': index - 1 === stepIndex }"
          />
        </div>
        <div class="tour__actions">
          <button type="button" class="tour__action tour__action--ghost" @click="skip">跳过</button>
          <button
            type="button"
            class="tour__action tour__action--ghost"
            :disabled="stepIndex === 0"
            @click="prev"
          >
            上一步
          </button>
          <button type="button" class="tour__action tour__action--primary" @click="next">
            {{ isLastStep ? '开始使用' : '下一步' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.tour {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--xl-space-4);
}

.tour__overlay {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--xl-text-primary) 45%, transparent);
}

.tour__card {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: var(--xl-space-6);
  border-radius: 16px;
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-lg);
}

.tour__steps {
  color: var(--xl-text-muted);
  font-size: 12px;
}

.tour__close {
  position: absolute;
  top: var(--xl-space-3);
  right: var(--xl-space-3);
  border: none;
  background: none;
  color: var(--xl-text-secondary);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.tour__close:hover {
  color: var(--xl-text-primary);
}

.tour__title {
  margin: var(--xl-space-3) 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 20px;
}

.tour__desc {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
  line-height: 1.8;
}

.tour__dots {
  display: flex;
  gap: 6px;
  margin: var(--xl-space-4) 0 0;
}

.tour__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--xl-border);
  transition: background var(--xl-transition);
}

.tour__dot--active {
  background: var(--xl-color-ai);
}

.tour__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--xl-space-2);
  margin-top: var(--xl-space-6);
}

.tour__action {
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color var(--xl-transition),
    color var(--xl-transition);
}

.tour__action:hover {
  border-color: var(--xl-color-primary);
  color: var(--xl-color-primary);
}

.tour__action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.tour__action--primary {
  border-color: var(--xl-color-ai);
  background: var(--xl-color-ai);
  color: #fff;
}

.tour__action--primary:hover {
  border-color: var(--xl-color-ai);
  color: #fff;
  opacity: 0.9;
}

.tour__action--ghost {
  margin-right: auto;
}
</style>
