<script setup lang="ts">
// 创作工作台（B09）：内容创作链路聚合入口（文章管理/写作/审核/发布）。
// 依赖各里程碑逐个接入：M04 文章管理；AI 写作随 M07、审核随 M10、发布随 M10 接入路由后启用。
import { RouterLink } from 'vue-router'
import { ChatLineRound, DocumentChecked, EditPen, Promotion } from '@element-plus/icons-vue'

/** 工作台入口定义：路由可用则展示。 */
const entries = [
  {
    title: '文章管理',
    description: '新建、编辑、自动保存草稿，管理公开/私有可见性（F-0301/F-0302/F-0307）',
    to: { name: 'article-list' },
    icon: DocumentChecked,
    enabled: true,
  },
  {
    title: 'AI 写作',
    description: '「小光」输入主题或草稿直接输出完整文章，AI 审校把关（F-0601/F-0604）',
    to: { name: 'writing' },
    icon: EditPen,
    enabled: true,
  },
  {
    title: '审核中心',
    description: '双闸门审核：AI 审校 + 人工审核，驳回需填写原因/位置/期望修改（F-0902/F-0904）',
    to: { name: 'review-center' },
    icon: ChatLineRound,
    enabled: true,
  },
  {
    title: '发布管理',
    description: '立即/定时发布，发布幂等防重复，发布成功自动建立 RAG 索引（F-0905/F-0402）',
    to: { name: 'release-list' },
    icon: Promotion,
    enabled: true,
  },
] as const
</script>

<template>
  <main class="workbench">
    <h1 class="workbench__title">创作工作台</h1>
    <p class="workbench__intro">
      从这里开始内容创作：写文章 → AI 审校 → 人工审核 → 发布 → 自动索引，形成完整闭环。
    </p>
    <ul class="workbench__entries">
      <li
        v-for="entry in entries"
        :key="entry.title"
        class="workbench__entry"
        :class="{ 'workbench__entry--disabled': !entry.enabled }"
      >
        <RouterLink v-if="entry.enabled" class="workbench__link" :to="entry.to">
          <div class="workbench__icon" aria-hidden="true">
            <el-icon><component :is="entry.icon" /></el-icon>
          </div>
          <h2 class="workbench__entry-title">{{ entry.title }}</h2>
          <p class="workbench__entry-desc">{{ entry.description }}</p>
        </RouterLink>
        <div v-else class="workbench__link">
          <div class="workbench__icon" aria-hidden="true">
            <el-icon><component :is="entry.icon" /></el-icon>
          </div>
          <h2 class="workbench__entry-title">{{ entry.title }}</h2>
          <p class="workbench__entry-desc">{{ entry.description }}</p>
          <span class="workbench__soon">即将上线</span>
        </div>
      </li>
    </ul>
  </main>
</template>

<style scoped>
.workbench {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px 64px;
}

.workbench__title {
  margin: 0;
  font-size: 24px;
}

.workbench__intro {
  margin: 8px 0 24px;
  color: var(--xl-text-secondary);
  font-size: 14px;
}

.workbench__entries {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.workbench__entry {
  border: 1px solid var(--xl-border);
  border-radius: var(--xl-radius-card);
  background: var(--xl-bg-surface);
  box-shadow: var(--xl-shadow-sm);
  overflow: hidden;
  transition:
    box-shadow var(--xl-transition),
    transform var(--xl-transition);
}

.workbench__entry--disabled {
  opacity: 0.65;
}

.workbench__entry:not(.workbench__entry--disabled):hover {
  box-shadow: var(--xl-shadow-md);
  transform: translateY(-2px);
}

.workbench__link {
  display: block;
  padding: 20px;
  height: 100%;
  color: inherit;
  text-decoration: none;
}

.workbench__entry:not(.workbench__entry--disabled) .workbench__link:hover {
  border-color: var(--xl-color-primary);
}

.workbench__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-bottom: 12px;
  border-radius: var(--xl-radius);
  background: color-mix(in srgb, var(--xl-color-primary) 10%, transparent);
  color: var(--xl-color-primary);
  font-size: 20px;
}

.workbench__entry:nth-child(2) .workbench__icon {
  background: color-mix(in srgb, var(--xl-color-ai) 10%, transparent);
  color: var(--xl-color-ai);
}

.workbench__entry-title {
  margin: 0 0 8px;
  font-size: 16px;
}

.workbench__entry-desc {
  margin: 0;
  color: var(--xl-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.workbench__soon {
  display: inline-block;
  margin-top: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--xl-bg-secondary);
  color: var(--xl-text-secondary);
  font-size: 12px;
}
</style>
