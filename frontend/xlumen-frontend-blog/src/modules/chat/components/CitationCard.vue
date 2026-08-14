<script setup lang="ts">
// 引用卡片（D01/D02）：[序号] 可展开篇名/段落，跳转原文 /knowledge/{knowledgeId}#{headingAnchor}。
import { computed, ref } from 'vue'

import type { Citation } from '@/modules/chat/api/chat'

const props = defineProps<{
  citation: Citation
  index: number
}>()

const expanded = ref(false)

const href = computed(() => {
  const base = `/knowledge/${props.citation.knowledgeId}`
  return props.citation.headingAnchor ? `${base}#${props.citation.headingAnchor}` : base
})
</script>

<template>
  <div class="citation-card">
    <button
      type="button"
      class="citation-card__toggle"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span class="citation-card__badge">[{{ index }}]</span>
      <span class="citation-card__title">{{ citation.title || '未命名知识' }}</span>
    </button>
    <div v-if="expanded" class="citation-card__body">
      <p class="citation-card__text">{{ citation.chunkText }}</p>
      <RouterLink class="citation-card__link" :to="href">查看原文段落</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.citation-card {
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-sm, 6px);
  background: var(--xl-bg-page);
}

.citation-card__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  border: none;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.citation-card__badge {
  color: var(--xl-color-ai);
  font-size: 12px;
  font-weight: 600;
}

.citation-card__title {
  flex: 1;
  min-width: 0;
  color: var(--xl-text-primary);
  font-size: 12px;
  overflow-wrap: break-word;
}

.citation-card__body {
  padding: 0 10px 10px;
}

.citation-card__text {
  margin: 0 0 8px;
  padding: 8px 10px;
  border-left: 2px solid var(--xl-color-ai);
  background: var(--xl-bg-surface);
  color: var(--xl-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: break-word;
}

.citation-card__link {
  color: var(--xl-color-ai);
  font-size: 12px;
  text-decoration: none;
}

.citation-card__link:hover {
  text-decoration: underline;
}
</style>
