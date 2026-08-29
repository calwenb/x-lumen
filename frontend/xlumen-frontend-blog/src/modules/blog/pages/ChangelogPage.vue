<script setup lang="ts">
// 更新日志（访客可见）：与知识库页同款布局——页头大标题 + 分隔线行式列表。
// 条目 = 左侧日期块 + 标题/发布日期 + Markdown 正文；分页加载由后端分页契约保证。
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchChangelogs } from '@/modules/blog/api/changelog'
import { renderMarkdown } from '@/modules/publishing/utils/markdown'

import type { ChangelogItem } from '@/modules/blog/api/changelog'

const PAGE_SIZE = 10

const items = ref<ChangelogItem[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)

/** 日期块：日号（如 25）。 */
function dayOf(value: string | null): string {
  return value ? value.slice(8, 10) : '—'
}

/** 日期块：年月（如 2026.08）。 */
function monthOf(value: string | null): string {
  return value ? `${value.slice(0, 4)}.${value.slice(5, 7)}` : '待发布'
}

/** 标签：完整日期。 */
function dateOf(value: string | null): string {
  return value ? value.slice(0, 10) : '待发布'
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
    <!-- 页头：大标题 + 描述（与知识库页同款） -->
    <header class="changelog__header">
      <h1 class="changelog__title">站点更新日志</h1>
      <p class="changelog__desc">记录 xLumen 的功能与内容更新，共 {{ total }} 条。</p>
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
      <div v-else class="changelog__list">
        <article v-for="item in items" :key="item.id" class="changelog__entry">
          <div class="changelog__cover" aria-hidden="true">
            <span class="changelog__cover-day">{{ dayOf(item.publishedAt) }}</span>
            <span class="changelog__cover-month">{{ monthOf(item.publishedAt) }}</span>
          </div>
          <div class="changelog__body">
            <div class="changelog__name-row">
              <h2 class="changelog__entry-title">{{ item.title }}</h2>
              <el-tag effect="plain" size="small" class="changelog__date-tag">
                发布于 {{ dateOf(item.publishedAt) }}
              </el-tag>
            </div>
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
  </main>
</template>

<style scoped>
.changelog {
  width: min(calc(100% - 48px), 1080px);
  margin: 0 auto;
  padding: var(--xl-space-8) var(--xl-content-pad) var(--xl-space-8);
  box-sizing: border-box;
}

/* ===== 页头 ===== */
.changelog__header {
  margin-bottom: var(--xl-space-6);
}

.changelog__title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h1);
  font-weight: var(--xl-fs-h1-w);
  line-height: var(--xl-fs-h1-lh);
  letter-spacing: var(--xl-fs-h1-track);
}

.changelog__desc {
  margin: 0;
  max-width: 640px;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

/* ===== 状态区 ===== */
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
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

/* ===== 行式列表（与知识库书架同款：分隔线 + 左块 + 内容） ===== */
.changelog__list {
  display: flex;
  flex-direction: column;
}

.changelog__entry {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--xl-space-6);
  align-items: start;
  padding: var(--xl-space-6) 0;
  border-bottom: 1px solid var(--xl-border);
}

.changelog__entry:first-child {
  border-top: 1px solid var(--xl-border);
}

/* 日期块：品牌渐变 + 日号/年月，对应知识库封面位 */
.changelog__cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 84px;
  height: 84px;
  flex-shrink: 0;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--xl-color-primary) 16%, transparent),
    var(--xl-bg-surface)
  );
}

.changelog__cover-day {
  color: var(--xl-color-primary);
  font-family: var(--xl-font-mono);
  font-size: 26px;
  font-weight: var(--xl-fs-title-w);
  line-height: 1;
}

.changelog__cover-month {
  color: var(--xl-text-muted);
  font-family: var(--xl-font-mono);
  font-size: var(--xl-fs-caption);
  line-height: 1;
}

.changelog__body {
  min-width: 0;
}

.changelog__name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--xl-space-3);
  margin-bottom: var(--xl-space-2);
}

.changelog__entry-title {
  margin: 0;
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

.changelog__date-tag {
  color: var(--xl-text-muted);
  font-family: var(--xl-font-mono);
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

@media (width <= 700px) {
  .changelog__entry {
    grid-template-columns: 1fr;
  }

  .changelog__cover {
    width: 64px;
    height: 64px;
  }
}
</style>
