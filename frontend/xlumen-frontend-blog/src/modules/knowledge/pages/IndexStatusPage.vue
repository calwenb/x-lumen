<script setup lang="ts">
// RAG 索引状态页（M05，~登录可见）：检索测试（结果列表：分数/篇名/段落/切片文本 + 跳原文）
// + 知识索引状态查询。
import { ref } from 'vue'

import { fetchIndexStatus, retrievalTest } from '@/modules/knowledge/api/knowledge'

import type { IndexStatus, RetrievalItem } from '@/modules/knowledge/api/knowledge'

const INDEX_STATUS_LABELS: Record<string, string> = {
  PENDING: '待索引',
  INDEXING: '索引中',
  INDEXED: '已索引',
  FAILED: '索引失败',
}

const VISIBILITY_LABELS: Record<number, string> = {
  0: '私有',
  1: '公开',
}

// 检索测试
const query = ref('')
const topK = ref(5)
const results = ref<RetrievalItem[]>([])
const searching = ref(false)
const searchError = ref('')
const searched = ref(false)

// 索引状态
const statusKnowledgeId = ref('')
const statusResult = ref<IndexStatus | null>(null)
const statusQueried = ref(false)
const statusLoading = ref(false)
const statusError = ref('')
const statusEmpty = ref(false)

function formatScore(score: number): string {
  return score.toFixed(4)
}

function formatTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

function knowledgeHref(item: RetrievalItem): string {
  return item.headingAnchor
    ? `/knowledge/${item.knowledgeId}#${item.headingAnchor}`
    : `/knowledge/${item.knowledgeId}`
}

async function search(): Promise<void> {
  const q = query.value.trim()
  if (!q || searching.value) return
  searching.value = true
  searchError.value = ''
  searched.value = true
  results.value = []
  try {
    results.value = await retrievalTest(q, Number(topK.value) || 5)
  } catch (error) {
    searchError.value = error instanceof Error ? error.message : '检索失败，请稍后重试'
  } finally {
    searching.value = false
  }
}

async function queryStatus(): Promise<void> {
  const id = statusKnowledgeId.value.trim()
  if (!id || statusLoading.value) return
  statusLoading.value = true
  statusError.value = ''
  statusQueried.value = true
  statusResult.value = null
  statusEmpty.value = false
  try {
    const status = await fetchIndexStatus(id)
    if (status) statusResult.value = status
    else statusEmpty.value = true
  } catch (error) {
    statusError.value = error instanceof Error ? error.message : '查询失败，请稍后重试'
  } finally {
    statusLoading.value = false
  }
}
</script>

<template>
  <main class="index-status">
    <header class="index-status__header">
      <h1 class="index-status__title">RAG 索引状态</h1>
      <p class="index-status__intro">测试「小光」的检索命中效果，或按知识 ID 查询索引建立状态。</p>
    </header>

    <div class="index-status__split">
      <section class="index-status__panel index-status__panel--explore">
        <h2 class="index-status__panel-title">检索测试</h2>
        <form class="index-status__form" @submit.prevent="search">
          <el-input
            v-model="query"
            class="index-status__input"
            placeholder="输入检索词，如：Spring Boot"
            clearable
          />
          <label class="index-status__topk">
            返回条数
            <el-input-number
              v-model="topK"
              :min="1"
              :max="20"
              size="small"
              class="index-status__topk-input"
            />
          </label>
          <el-button type="primary" native-type="submit" :loading="searching">
            {{ searching ? '检索中' : '测试检索' }}
          </el-button>
        </form>

        <p v-if="searchError" class="index-status__error" role="alert">{{ searchError }}</p>

        <template v-if="searched">
          <p v-if="results.length === 0" class="index-status__empty">没有命中任何片段。</p>
          <ul v-else class="index-status__results">
            <li
              v-for="item in results"
              :key="`${item.knowledgeId}-${item.chunkSeq}`"
              class="retrieval-item"
            >
              <div class="retrieval-item__head">
                <span class="retrieval-item__score">{{ formatScore(item.score) }}</span>
                <RouterLink class="retrieval-item__title" :to="knowledgeHref(item)">
                  {{ item.title || '未命名知识' }}
                </RouterLink>
                <span class="retrieval-item__meta">
                  <span v-if="item.headingAnchor">段落：{{ item.headingAnchor }}</span>
                  <span>{{ VISIBILITY_LABELS[item.visibility] ?? item.visibility }}</span>
                </span>
              </div>
              <p class="retrieval-item__text">{{ item.chunkText }}</p>
              <div class="retrieval-item__foot">
                <RouterLink class="retrieval-item__link" :to="knowledgeHref(item)">
                  跳转原文 →
                </RouterLink>
              </div>
            </li>
          </ul>
        </template>
      </section>

      <section class="index-status__panel index-status__panel--verify">
        <h2 class="index-status__panel-title">知识索引状态查询</h2>
        <form class="index-status__form" @submit.prevent="queryStatus">
          <el-input
            v-model="statusKnowledgeId"
            class="index-status__input"
            placeholder="输入知识 ID"
            clearable
          />
          <el-button type="primary" native-type="submit" :loading="statusLoading">
            {{ statusLoading ? '查询中' : '查询' }}
          </el-button>
        </form>

        <p v-if="statusError" class="index-status__error" role="alert">{{ statusError }}</p>

        <template v-if="statusQueried">
          <p v-if="statusEmpty" class="index-status__empty">该知识尚未建立索引。</p>
          <div v-else-if="statusResult" class="index-status__card">
            <p>
              <strong>状态：</strong
              >{{ INDEX_STATUS_LABELS[statusResult.status] ?? statusResult.status }}
            </p>
            <p><strong>版本：</strong>v{{ statusResult.version }}</p>
            <p><strong>切片数：</strong>{{ statusResult.chunkCount }}</p>
            <p>
              <strong>索引时间：</strong
              >{{ statusResult.indexedAt ? formatTime(statusResult.indexedAt) : '—' }}
            </p>
          </div>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.index-status {
  max-width: 1120px;
  margin: 0 auto;
  padding: var(--xl-space-6) var(--xl-space-4) var(--xl-space-8);
}

.index-status__header {
  margin-bottom: var(--xl-space-6);
}

.index-status__title {
  margin: 0 0 var(--xl-space-2);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-h2);
  font-weight: var(--xl-fs-h2-w);
  letter-spacing: var(--xl-fs-h2-track);
}

.index-status__intro {
  max-width: 640px;
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
  line-height: 1.7;
}

.index-status__split {
  display: grid;
  grid-template-columns: 58fr 42fr;
  gap: var(--xl-space-6);
  align-items: start;
}

.index-status__panel {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

/* 核验区更紧凑：以 Paper 底色弱化，与探索区区分密度 */
.index-status__panel--verify {
  background: var(--xl-bg-page);
  box-shadow: none;
}

.index-status__panel-title {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-title);
  font-weight: var(--xl-fs-title-w);
}

.index-status__form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-3);
}

.index-status__input {
  flex: 1;
  min-width: 200px;
}

.index-status__topk {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
}

.index-status__topk-input {
  width: 120px;
}

.index-status__error {
  margin: 0 0 var(--xl-space-3);
  color: var(--xl-color-danger);
  font-size: var(--xl-fs-caption);
}

.index-status__empty {
  padding: var(--xl-space-6) 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-body);
}

.index-status__results {
  display: flex;
  flex-direction: column;
  gap: var(--xl-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.retrieval-item {
  padding: var(--xl-space-3) var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.retrieval-item__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--xl-space-2);
  margin-bottom: var(--xl-space-2);
}

.retrieval-item__score {
  color: var(--xl-color-ai);
  font-family: var(--xl-font-mono);
  font-size: var(--xl-fs-caption);
  font-weight: 600;
}

.retrieval-item__title {
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-body);
  font-weight: 600;
  text-decoration: none;
}

.retrieval-item__title:hover {
  text-decoration: underline;
}

.retrieval-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xl-space-2);
  color: var(--xl-text-muted);
  font-size: var(--xl-fs-caption);
}

.retrieval-item__text {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: var(--xl-fs-caption);
  line-height: 1.7;
  overflow-wrap: break-word;
}

.retrieval-item__foot {
  margin-top: var(--xl-space-2);
  text-align: right;
}

.retrieval-item__link {
  color: var(--xl-color-primary);
  font-size: var(--xl-fs-caption);
  font-weight: 600;
  text-decoration: none;
}

.retrieval-item__link:hover {
  text-decoration: underline;
}

.index-status__card {
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-surface);
  color: var(--xl-text-primary);
  font-size: var(--xl-fs-body);
  line-height: 1.9;
}

.index-status__card p {
  margin: 0;
}

@media (width <= 880px) {
  .index-status__split {
    grid-template-columns: 1fr;
  }
}
</style>
