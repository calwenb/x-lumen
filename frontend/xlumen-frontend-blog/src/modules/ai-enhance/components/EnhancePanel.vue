<script setup lang="ts">
// AI 增值面板（M09，F-0801/F-0802）：场景切换（摘要/SEO）+ 结果卡片 + 应用到正文/复制/重新生成。
// 通过 emit('apply', field, value) 交由编辑器页决定字段落点（如 title/keywords/description/summary）。
import { computed, ref } from 'vue'

import { enhanceArticle, parseEnhanceResult } from '@/modules/ai-enhance/api/enhance'

import type { EnhanceScene } from '@/modules/ai-enhance/api/enhance'

const props = defineProps<{
  articleId?: string
  content: string
}>()

const emit = defineEmits<{
  apply: [field: string, value: string]
}>()

const SCENES: ReadonlyArray<{ value: EnhanceScene; label: string }> = [
  { value: 'SUMMARY', label: '摘要' },
  { value: 'SEO', label: 'SEO 优化' },
]

const FIELD_LABELS: Record<string, string> = {
  summary: '摘要',
  title: '标题',
  keywords: '关键词',
  description: '描述',
}

const scene = ref<EnhanceScene>('SUMMARY')
const loading = ref(false)
const error = ref('')
const result = ref<Record<string, string> | null>(null)

const entries = computed(() => (result.value ? Object.entries(result.value) : []))

async function generate(): Promise<void> {
  if (!props.content.trim()) {
    error.value = '请先填写正文内容'
    return
  }
  loading.value = true
  error.value = ''
  result.value = null
  try {
    const res = await enhanceArticle({
      ...(props.articleId ? { articleId: props.articleId } : {}),
      scene: scene.value,
      content: props.content,
    })
    result.value = parseEnhanceResult(res.resultJson)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '生成失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function copyValue(value: string): void {
  void navigator.clipboard.writeText(value)
}
</script>

<template>
  <section class="enhance-panel">
    <div class="enhance-panel__toolbar">
      <div class="enhance-panel__scenes" role="tablist" aria-label="AI 增值场景">
        <button
          v-for="item in SCENES"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="scene === item.value"
          class="enhance-panel__scene"
          :class="{ 'enhance-panel__scene--active': scene === item.value }"
          @click="scene = item.value"
        >
          {{ item.label }}
        </button>
      </div>
      <el-button type="primary" :loading="loading" @click="generate">
        {{ loading ? '生成中' : '生成' }}
      </el-button>
    </div>

    <p v-if="error" class="enhance-panel__error" role="alert">{{ error }}</p>

    <div v-if="result" class="enhance-panel__results">
      <div v-for="[field, value] in entries" :key="field" class="enhance-panel__card">
        <div class="enhance-panel__card-head">
          <span class="enhance-panel__card-label">{{ FIELD_LABELS[field] ?? field }}</span>
          <div class="enhance-panel__card-actions">
            <el-button type="primary" link size="small" @click="emit('apply', field, value)"
              >应用</el-button
            >
            <el-button type="primary" link size="small" @click="copyValue(value)">复制</el-button>
          </div>
        </div>
        <p class="enhance-panel__card-value">{{ value }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.enhance-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--xl-space-4);
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
}

.enhance-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.enhance-panel__scenes {
  display: flex;
  gap: 6px;
}

.enhance-panel__scene {
  padding: 6px 14px;
  border: 1px solid var(--xl-border);
  border-radius: 999px;
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.enhance-panel__scene--active {
  border-color: var(--xl-color-primary);
  background: color-mix(in srgb, var(--xl-color-primary) 8%, transparent);
  color: var(--xl-color-primary);
}

.enhance-panel__generate {
  padding: 7px 16px;
  border: none;
  border-radius: 8px;
  background: var(--xl-color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.enhance-panel__generate:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.enhance-panel__generate:hover:not(:disabled) {
  background: var(--xl-color-primary-hover);
}

.enhance-panel__error {
  margin: 0;
  color: var(--xl-color-danger);
  font-size: 13px;
}

.enhance-panel__results {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.enhance-panel__card {
  padding: 12px 14px;
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm);
  background: var(--xl-bg-page);
}

.enhance-panel__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.enhance-panel__card-label {
  color: var(--xl-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.enhance-panel__card-actions {
  display: flex;
  gap: 8px;
}

.enhance-panel__card-value {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}
</style>
