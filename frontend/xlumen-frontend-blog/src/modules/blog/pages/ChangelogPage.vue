<script setup lang="ts">
// 更新日志（访客可见）：时间线展示站点迭代动态。
// 条目 = 标题 + Markdown 正文 + 发布于日期；分页加载由后端分页契约保证。
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Tickets } from '@element-plus/icons-vue'

import { fetchChangelogs } from '@/modules/blog/api/changelog'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'

import type { ChangelogItem } from '@/modules/blog/api/changelog'

const PAGE_SIZE = 10

const items = ref<ChangelogItem[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)

function formatTimestamp(value: string | null): string {
  return value ? `发布于 ${value.slice(0, 10)}` : '待发布'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const result = await fetchChangelogs({ pageNo: page.value, pageSize: PAGE_SIZE })
    items.value = result.records
    total.value = result.total
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

watch(page, () => {
  void load()
})
</script>

<template>
  <main class="changelog">
    <header class="changelog__head">
      <h1 class="changelog__title">
        <el-icon class="changelog__title-icon"><Tickets /></el-icon>
        更新日志
      </h1>
      <p class="changelog__desc">记录站点迭代：新功能、体验优化与问题修复，共 {{ total }} 条。</p>
    </header>

    <div v-if="loading" class="changelog__state">
      <div v-for="i in 3" :key="i" class="changelog__skeleton" aria-hidden="true" />
    </div>
    <div v-else-if="loadError" class="changelog__state">
      <p class="changelog__state-text">更新日志加载失败</p>
      <el-button type="primary" plain @click="load">重试</el-button>
    </div>
    <template v-else>
      <el-empty v-if="items.length === 0" description="暂无更新日志" />
      <el-timeline v-else class="changelog__timeline">
        <el-timeline-item
          v-for="item in items"
          :key="item.id"
          :timestamp="formatTimestamp(item.publishedAt)"
          placement="top"
        >
          <article class="changelog__entry">
            <h2 class="changelog__entry-title">{{ item.title }}</h2>
            <div
              class="changelog__entry-content markdown-body"
              v-html="renderMarkdown(item.content)"
            />
          </article>
        </el-timeline-item>
      </el-timeline>
      <div v-if="total > PAGE_SIZE" class="changelog__pager">
        <el-pagination
          v-model:current-page="page"
          layout="prev, pager, next"
          :page-size="PAGE_SIZE"
          :total="total"
          background
        />
      </div>
      <p class="changelog__back">
        <RouterLink to="/">← 返回首页</RouterLink>
      </p>
    </template>
  </main>
</template>

<style scoped>
.changelog {
  width: min(calc(100% - 48px), 760px);
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
  box-sizing: border-box;
}

.changelog__head {
  margin-bottom: var(--xl-space-6);
}

.changelog__title {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 22px;
}

.changelog__title-icon {
  color: var(--xl-color-primary);
  font-size: 20px;
}

.changelog__desc {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
}

.changelog__state {
  padding: var(--xl-space-8) 0;
  text-align: center;
}

.changelog__skeleton {
  height: 96px;
  margin-bottom: var(--xl-space-4);
  border-radius: var(--xl-radius-card);
  background: color-mix(in srgb, var(--xl-border) 60%, transparent);
}

.changelog__state-text {
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.changelog__timeline {
  padding-left: var(--xl-space-2);
}

.changelog__entry {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.changelog__entry-title {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: 16px;
}

.changelog__entry-content {
  margin-top: 0;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.changelog__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--xl-space-6);
}

.changelog__back {
  margin: var(--xl-space-6) 0 0;
  text-align: center;
}

.changelog__back a {
  color: var(--xl-color-primary);
  font-size: 13px;
  text-decoration: none;
}

.changelog__back a:hover {
  text-decoration: underline;
}
</style>
