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
    <!-- 左侧窄轨（24） -->
    <aside class="changelog__rail">
      <h1 class="changelog__title">
        <el-icon class="changelog__title-icon"><Tickets /></el-icon>
        站点更新日志
      </h1>
      <p class="changelog__desc">记录 xLumen 的功能与内容更新，共 {{ total }} 条。</p>
    </aside>

    <!-- 右侧动态流（76） -->
    <section class="changelog__stream">
      <div v-if="loading" class="changelog__state">
        <div v-for="i in 3" :key="i" class="changelog__skeleton" aria-hidden="true" />
      </div>
      <div v-else-if="loadError" class="changelog__state">
        <p class="changelog__state-text">更新日志加载失败</p>
        <el-button type="primary" plain @click="load">重试</el-button>
      </div>
      <template v-else>
        <el-empty v-if="items.length === 0" description="暂无更新日志" />
        <div v-else class="changelog__list">
          <article v-for="item in items" :key="item.id" class="changelog__entry">
            <time class="changelog__date">{{ formatTimestamp(item.publishedAt) }}</time>
            <span class="changelog__node" aria-hidden="true"></span>
            <div class="changelog__content">
              <h2 class="changelog__entry-title">{{ item.title }}</h2>
              <div
                class="changelog__entry-content markdown-body"
                v-html="renderMarkdown(item.content)"
              />
            </div>
          </article>
        </div>
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
    </section>
  </main>
</template>

<style scoped>
.changelog {
  display: grid;
  grid-template-columns: 24% minmax(0, 1fr);
  gap: var(--xl-space-8);
  align-items: start;
  width: min(calc(100% - 48px), 1080px);
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

/* ===== 左侧窄轨 ===== */
.changelog__rail {
  position: sticky;
  top: calc(var(--xl-header-h) + var(--xl-space-6));
  padding-top: var(--xl-space-6);
  border-top: 2px solid var(--xl-color-primary);
}

.changelog__title {
  display: flex;
  align-items: center;
  gap: var(--xl-space-2);
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.changelog__title-icon {
  color: var(--xl-color-primary);
  font-size: 18px;
}

.changelog__desc {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

/* ===== 右侧动态流 ===== */
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
  font-size: var(--xl-fs-body);
}

.changelog__list {
  display: flex;
  flex-direction: column;
}

.changelog__entry {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--xl-space-6);
  padding: var(--xl-space-6) 0;
  position: relative;
}

.changelog__date {
  color: var(--xl-text-muted);
  font-family: var(--xl-font-mono);
  font-size: var(--xl-fs-caption);
  padding-top: 4px;
}

.changelog__node {
  position: absolute;
  left: 118px;
  top: var(--xl-space-6);
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--xl-color-primary);
}

.changelog__content {
  min-width: 0;
  padding-left: var(--xl-space-4);
  border-left: 1px solid var(--xl-border);
}

.changelog__entry-title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

.changelog__entry-content {
  margin-top: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.8;
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
  font-size: var(--xl-fs-caption);
  text-decoration: none;
}

.changelog__back a:hover {
  text-decoration: underline;
}

@media (width <= 800px) {
  .changelog {
    grid-template-columns: 1fr;
  }

  .changelog__rail {
    position: static;
  }
}
</style>
